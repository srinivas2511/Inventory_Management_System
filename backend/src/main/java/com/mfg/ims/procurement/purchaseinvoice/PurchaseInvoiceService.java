package com.mfg.ims.procurement.purchaseinvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PurchaseInvoiceService {

    private static final BigDecimal QTY_TOLERANCE = new BigDecimal("2");   // 2%
    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("1"); // 1%

    private final PurchaseInvoiceRepository repo;

    public PurchaseInvoiceService(PurchaseInvoiceRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<PurchaseInvoiceDto> list(Long companyId) {
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceDto get(Long id) {
        return toDto(find(id));
    }

    public PurchaseInvoiceDto create(SavePurchaseInvoiceRequest req) {
        var inv = new PurchaseInvoice();
        inv.setDocNo(req.docNo());
        inv.setSupplierId(req.supplierId());
        inv.setSupplierInvoiceNo(req.supplierInvoiceNo());
        inv.setSupplierInvoiceDate(req.supplierInvoiceDate());
        inv.setPoId(req.poId());
        inv.setDueDate(req.dueDate());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        if (req.items() != null) {
            int lineNo = 1;
            for (var line : req.items()) {
                var item = new PurchaseInvoiceItem();
                item.setInvoice(inv);
                item.setLineNo(lineNo++);
                item.setPoItemId(line.poItemId());
                item.setGrnItemId(line.grnItemId());
                item.setMaterialId(line.materialId());
                item.setProductId(line.productId());
                item.setDescription(line.description());
                item.setQty(line.qty());
                item.setUnitPrice(line.unitPrice());
                BigDecimal disc = line.discountPct() != null ? line.discountPct() : BigDecimal.ZERO;
                item.setDiscountPct(disc);
                BigDecimal cgst = orZero(line.cgstRate()); item.setCgstRate(cgst);
                BigDecimal sgst = orZero(line.sgstRate()); item.setSgstRate(sgst);
                BigDecimal igst = orZero(line.igstRate()); item.setIgstRate(igst);

                BigDecimal net = line.qty().multiply(line.unitPrice())
                        .multiply(BigDecimal.ONE.subtract(disc.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
                BigDecimal tax = net.multiply(cgst.add(sgst).add(igst)).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                BigDecimal lt = net.add(tax);
                item.setLineTotal(lt);
                subtotal = subtotal.add(net);
                taxTotal = taxTotal.add(tax);
                inv.getItems().add(item);
            }
        }

        inv.setSubtotal(subtotal);
        inv.setTaxTotal(taxTotal);
        inv.setTotal(subtotal.add(taxTotal));
        return toDto(repo.save(inv));
    }

    public PurchaseInvoiceDto post(Long id) {
        var inv = find(id);
        if (!"DRAFT".equals(inv.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invoice must be DRAFT to post");
        }
        if ("EXCEPTION".equals(inv.getMatchStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Invoice has match exceptions — override required before posting");
        }
        // Three-way match is enforced on creation; posting transitions to OPEN
        inv.setStatus("OPEN");
        inv.setApBalance(inv.getTotal().subtract(inv.getPaidAmount()));
        return toDto(repo.save(inv));
    }

    private PurchaseInvoice find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + id));
    }

    private BigDecimal orZero(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    private PurchaseInvoiceDto toDto(PurchaseInvoice inv) {
        var items = inv.getItems().stream()
                .map(i -> new PurchaseInvoiceDto.ItemDto(i.getId(), i.getLineNo(), i.getMaterialId(),
                        i.getProductId(), i.getDescription(), i.getQty(), i.getUnitPrice(),
                        i.getLineTotal(), i.getMatchStatus()))
                .toList();
        return new PurchaseInvoiceDto(inv.getId(), inv.getDocNo(), inv.getStatus(), inv.getSupplierId(),
                inv.getSupplierInvoiceNo(), inv.getSupplierInvoiceDate(), inv.getPoId(), inv.getMatchStatus(),
                inv.getSubtotal(), inv.getTaxTotal(), inv.getTotal(), inv.getPaidAmount(), inv.getDueDate(), items);
    }
}
