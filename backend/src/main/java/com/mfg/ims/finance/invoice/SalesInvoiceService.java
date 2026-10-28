package com.mfg.ims.finance.invoice;

import com.mfg.ims.common.numbering.DocumentNumberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class SalesInvoiceService {

    private final SalesInvoiceRepository repo;
    private final DocumentNumberService numberService;

    public SalesInvoiceService(SalesInvoiceRepository repo, DocumentNumberService numberService) {
        this.repo = repo;
        this.numberService = numberService;
    }

    @Transactional(readOnly = true)
    public List<SalesInvoiceDto> list() {
        return repo.findByCompanyIdOrderByDocDateDesc(1L).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SalesInvoiceDto get(Long id) {
        return toDto(find(id));
    }

    public SalesInvoiceDto create(SaveInvoiceRequest req, Long userId) {
        var inv = new SalesInvoice();
        inv.setDocNo(numberService.next("SI"));
        inv.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        inv.setCustomerId(req.customerId());
        if (req.invoiceKind() != null) inv.setInvoiceKind(req.invoiceKind());
        if (req.supplyType() != null) inv.setSupplyType(req.supplyType());
        inv.setPlaceOfSupply(req.placeOfSupply());
        inv.setOcId(req.ocId());
        inv.setSalesOrderId(req.salesOrderId());
        inv.setDispatchId(req.dispatchId());
        inv.setCustomerPoNo(req.customerPoNo());
        inv.setDueDate(req.dueDate());
        inv.setCurrency(req.currency() != null ? req.currency() : "INR");
        inv.setRemarks(req.remarks());

        BigDecimal total = BigDecimal.ZERO;
        if (req.items() != null) {
            int lineNo = 1;
            for (var line : req.items()) {
                var item = new SalesInvoiceItem();
                item.setInvoice(inv);
                item.setLineNo(lineNo++);
                item.setProductId(line.productId());
                item.setMaterialId(line.materialId());
                item.setDescription(line.description());
                item.setQty(line.qty());
                item.setUom(line.uom());
                item.setRate(line.rate());
                item.setHsnSac(line.hsnSac());
                if (line.discountPct() != null) item.setDiscountPct(line.discountPct());
                if (line.rate() != null && line.qty() != null) {
                    BigDecimal disc = item.getDiscountPct() != null ? item.getDiscountPct() : BigDecimal.ZERO;
                    BigDecimal lineTotal = line.qty().multiply(line.rate())
                            .multiply(BigDecimal.ONE.subtract(disc.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                            .setScale(4, RoundingMode.HALF_UP);
                    item.setLineTotal(lineTotal);
                    item.setTaxableValue(lineTotal);
                    total = total.add(lineTotal);
                }
                inv.getItems().add(item);
            }
        }
        inv.setTaxableAmount(total);
        inv.setGrandTotal(total);
        inv.setBalanceInr(total);

        return toDto(repo.save(inv));
    }

    public SalesInvoiceDto post(Long id, Long userId) {
        var inv = find(id);
        if (!"DRAFT".equals(inv.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only DRAFT invoices can be posted");
        }
        inv.setStatus("POSTED");
        inv.setPostedAt(Instant.now());
        inv.setPostedBy(userId);
        return toDto(repo.save(inv));
    }

    public SalesInvoiceDto cancel(Long id, String reason, Long userId) {
        var inv = find(id);
        if ("CANCELLED".equals(inv.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice is already cancelled");
        }
        inv.setStatus("CANCELLED");
        inv.setCancelledReason(reason);
        return toDto(repo.save(inv));
    }

    private SalesInvoice find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Sales invoice not found: " + id));
    }

    private SalesInvoiceDto toDto(SalesInvoice inv) {
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
                inv.getPostedAt(), inv.getRemarks(), items);
    }
}
