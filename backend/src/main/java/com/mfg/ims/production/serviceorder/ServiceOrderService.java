package com.mfg.ims.production.serviceorder;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.numbering.DocumentNumberService;
import com.mfg.ims.finance.invoice.SalesInvoice;
import com.mfg.ims.finance.invoice.SalesInvoiceDto;
import com.mfg.ims.finance.invoice.SalesInvoiceItem;
import com.mfg.ims.finance.invoice.SalesInvoiceRepository;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.service.InventoryPostingService;
import com.mfg.ims.procurement.domain.GoodsReceipt;
import com.mfg.ims.procurement.domain.GoodsReceiptItem;
import com.mfg.ims.procurement.dto.GoodsReceiptDto;
import com.mfg.ims.procurement.repository.GoodsReceiptItemRepository;
import com.mfg.ims.procurement.repository.GoodsReceiptRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServiceOrderService {

    private final ServiceOrderRepository repo;
    private final DocumentNumberService docNos;
    private final ServiceConfirmationRepository confirmRepo;
    private final GoodsReceiptRepository grRepo;
    private final GoodsReceiptItemRepository grItemRepo;
    private final InventoryPostingService postingService;
    private final SalesInvoiceRepository salesInvoiceRepo;

    public ServiceOrderService(ServiceOrderRepository repo,
                                DocumentNumberService docNos,
                                ServiceConfirmationRepository confirmRepo,
                                GoodsReceiptRepository grRepo,
                                GoodsReceiptItemRepository grItemRepo,
                                InventoryPostingService postingService,
                                SalesInvoiceRepository salesInvoiceRepo) {
        this.repo = repo;
        this.docNos = docNos;
        this.confirmRepo = confirmRepo;
        this.grRepo = grRepo;
        this.grItemRepo = grItemRepo;
        this.postingService = postingService;
        this.salesInvoiceRepo = salesInvoiceRepo;
    }

    public ServiceOrderDto create(CreateServiceOrderRequest req, Long actorId) {
        ServiceOrder so = new ServiceOrder();
        so.setDocNo(docNos.next("SVO"));
        so.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        if (req.serviceKind() != null) so.setServiceKind(req.serviceKind());
        so.setVendorId(req.vendorId());
        so.setCustomerId(req.customerId());
        so.setDescription(req.description());
        so.setScheduledDate(req.scheduledDate());
        if (req.serviceCharge() != null) so.setServiceCharge(req.serviceCharge());
        if (req.taxAmount() != null) {
            so.setTaxAmount(req.taxAmount());
            BigDecimal charge = req.serviceCharge() != null ? req.serviceCharge() : BigDecimal.ZERO;
            so.setGrandTotal(charge.add(req.taxAmount()));
        }
        so.setRemarks(req.remarks());
        so.setCreatedBy(actorId);
        so = repo.save(so);
        return toDto(so);
    }

    @Transactional(readOnly = true)
    public List<ServiceOrderDto> list() {
        return repo.findByCompanyIdOrderByIdDesc(1L).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServiceOrderDto get(Long id) {
        return toDto(findOrThrow(id));
    }

    public ServiceOrderDto complete(Long id, Long actorId) {
        ServiceOrder so = findOrThrow(id);
        so.setStatus("COMPLETED");
        so.setCompletedDate(LocalDate.now());
        so.setUpdatedBy(actorId);
        return toDto(repo.save(so));
    }

    /** Confirm the service delivery — creates a confirmation record and advances status to IN_PROGRESS. */
    public ServiceConfirmationDto confirm(Long id, ConfirmServiceRequest req, Long actorId) {
        ServiceOrder so = findOrThrow(id);
        ServiceConfirmation conf = new ServiceConfirmation();
        conf.setSoId(so.getId());
        conf.setConfirmedAt(Instant.now());
        conf.setConfirmedBy(actorId);
        if (req != null) conf.setRemarks(req.remarks());
        conf = confirmRepo.save(conf);
        // Advance status if still DRAFT or CONFIRMED
        if ("DRAFT".equals(so.getStatus()) || "CONFIRMED".equals(so.getStatus())) {
            so.setStatus("IN_PROGRESS");
            so.setUpdatedBy(actorId);
            repo.save(so);
        }
        return toConfirmDto(conf);
    }

    @Transactional(readOnly = true)
    public List<ServiceConfirmationDto> getConfirmations(Long id) {
        findOrThrow(id); // ensure exists
        return confirmRepo.findBySoIdOrderByConfirmedAtDesc(id).stream()
                .map(this::toConfirmDto).collect(Collectors.toList());
    }

    /**
     * Create a goods-receipt inward linked to a service confirmation.
     * Posts stock IN via InventoryPostingService with transaction type SERVICE_INWARD.
     */
    public GoodsReceiptDto createInwardFromConfirmation(Long confId, CreateGrnFromConfRequest req, Long actorId) {
        ServiceConfirmation conf = confirmRepo.findById(confId)
                .orElseThrow(() -> new NotFoundException("ServiceConfirmation", confId));

        GoodsReceipt gr = new GoodsReceipt();
        gr.setGrNumber(docNos.next("GR"));
        gr.setServiceConfirmationId(confId);
        gr.setReceiptDate(req.receivedDate() != null ? req.receivedDate() : LocalDate.now());
        gr.setWarehouseId(req.warehouseId());
        gr.setStatus("POSTED");
        gr.setReceivedBy(actorId);
        gr = grRepo.save(gr);

        List<GoodsReceiptItem> savedItems = new ArrayList<>();
        if (req.items() != null) {
            for (CreateGrnFromConfRequest.ItemLine line : req.items()) {
                GoodsReceiptItem item = new GoodsReceiptItem();
                item.setGoodsReceipt(gr);
                item.setMaterialId(line.materialId());
                item.setReceivedQty(line.receivedQty());
                item.setLocationId(req.locationId());
                item.setInspectionRequired(false);
                item = grItemRepo.save(item);

                // Post stock IN as AVAILABLE (service inward — no incoming inspection needed)
                postingService.post(new PostingCommand(
                        "SERVICE_INWARD", "MATERIAL",
                        line.materialId(), null,
                        null, null,
                        req.warehouseId(), req.locationId(),
                        "AVAILABLE",
                        line.receivedQty(), null,
                        line.unitPrice() != null ? line.unitPrice() : BigDecimal.ZERO,
                        "GOODS_RECEIPT", gr.getId(), gr.getGrNumber(),
                        "Service inward GR: " + gr.getGrNumber()));

                savedItems.add(item);
            }
        }

        List<GoodsReceiptDto.GrItemDto> itemDtos = savedItems.stream()
                .map(i -> new GoodsReceiptDto.GrItemDto(
                        i.getId(), i.getPoItemId(), i.getMaterialId(), null,
                        i.getReceivedQty(), i.getLocationId(),
                        null, null, i.getMaterialBatchId(), null,
                        i.isInspectionRequired(), null))
                .toList();
        return new GoodsReceiptDto(gr.getId(), gr.getGrNumber(), null, null,
                gr.getReceiptDate(), null, null, gr.getWarehouseId(),
                gr.getStatus(), gr.getReceivedBy(), itemDtos, confId);
    }

    /**
     * Create a sales invoice linked to a service confirmation.
     */
    public SalesInvoiceDto createInvoiceFromConfirmation(Long confId, ServiceInvoiceRequest req, Long actorId) {
        ServiceConfirmation conf = confirmRepo.findById(confId)
                .orElseThrow(() -> new NotFoundException("ServiceConfirmation", confId));

        SalesInvoice inv = new SalesInvoice();
        inv.setDocNo(docNos.next("SI"));
        inv.setDocDate(req.invoiceDate() != null ? req.invoiceDate() : LocalDate.now());
        inv.setCustomerId(req.customerId() != null ? req.customerId() : resolveCustomerFromConf(conf));
        inv.setInvoiceKind("SERVICE");
        inv.setServiceConfirmationId(confId);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        if (req.lines() != null) {
            int lineNo = 1;
            for (ServiceInvoiceRequest.LineItem line : req.lines()) {
                SalesInvoiceItem item = new SalesInvoiceItem();
                item.setInvoice(inv);
                item.setLineNo(lineNo++);
                item.setItemKind("SERVICE");
                item.setDescription(line.description());
                BigDecimal qty = line.qty() != null ? line.qty() : BigDecimal.ONE;
                BigDecimal rate = line.unitPrice() != null ? line.unitPrice() : BigDecimal.ZERO;
                item.setQty(qty);
                item.setRate(rate);
                BigDecimal lineTotal = qty.multiply(rate).setScale(4, RoundingMode.HALF_UP);
                item.setTaxableValue(lineTotal);
                BigDecimal taxRate = line.taxRate() != null ? line.taxRate() : BigDecimal.ZERO;
                BigDecimal taxAmt = lineTotal
                        .multiply(taxRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
                        .setScale(4, RoundingMode.HALF_UP);
                item.setTaxAmount(taxAmt);
                item.setLineTotal(lineTotal.add(taxAmt));
                inv.getItems().add(item);
                total = total.add(lineTotal);
                totalTax = totalTax.add(taxAmt);
            }
        }
        inv.setTaxableAmount(total);
        inv.setTaxAmount(totalTax);
        inv.setGrandTotal(total.add(totalTax));
        inv.setBalanceInr(total.add(totalTax));

        SalesInvoice saved = salesInvoiceRepo.save(inv);
        return toDtoFromInvoice(saved);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private Long resolveCustomerFromConf(ServiceConfirmation conf) {
        // Fall back to the customer on the associated service order if available
        return repo.findById(conf.getSoId())
                .map(ServiceOrder::getCustomerId)
                .orElse(null);
    }

    private ServiceOrder findOrThrow(Long id) {
        return repo.findByIdAndCompanyId(id, 1L)
                .orElseThrow(() -> new NotFoundException("ServiceOrder", id));
    }

    private ServiceOrderDto toDto(ServiceOrder so) {
        return new ServiceOrderDto(so.getId(), so.getDocNo(), so.getDocDate(), so.getStatus(),
                so.getServiceKind(), so.getVendorId(), so.getCustomerId(), so.getDescription(),
                so.getScheduledDate(), so.getCompletedDate(), so.getServiceCharge(),
                so.getTaxAmount(), so.getGrandTotal(), so.getRemarks(), so.getCreatedAt());
    }

    private ServiceConfirmationDto toConfirmDto(ServiceConfirmation c) {
        return new ServiceConfirmationDto(c.getId(), c.getSoId(), c.getConfirmedAt(),
                c.getConfirmedBy(), c.getRemarks());
    }

    private SalesInvoiceDto toDtoFromInvoice(SalesInvoice inv) {
        var items = inv.getItems().stream()
                .map(i -> new SalesInvoiceDto.ItemDto(i.getId(), i.getLineNo(), i.getItemKind(),
                        i.getMaterialId(), i.getProductId(), i.getDescription(),
                        i.getQty(), i.getUom(), i.getRate(), i.getDiscountPct(),
                        i.getTaxableValue(), i.getTaxAmount(), i.getLineTotal(), i.getHsnSac()))
                .toList();
        return new SalesInvoiceDto(inv.getId(), inv.getDocNo(), inv.getDocDate(), inv.getStatus(),
                inv.getInvoiceKind(), inv.getCustomerId(), inv.getPlaceOfSupply(), inv.getSupplyType(),
                inv.isReverseCharge(), inv.getSalesOrderId(), inv.getOcId(), inv.getDispatchId(),
                inv.getCustomerPoNo(), inv.getDueDate(), inv.getCurrency(), inv.getExchangeRate(),
                inv.getTaxableAmount(), inv.getTaxAmount(), inv.getTcsAmount(), inv.getGrandTotal(),
                inv.getBalanceInr(), inv.getEinvoiceStatus(), inv.getIrn(), inv.getCancelledReason(),
                inv.getPostedAt(), inv.getRemarks(), items, inv.getServiceConfirmationId());
    }
}
