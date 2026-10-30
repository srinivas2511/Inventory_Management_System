package com.mfg.ims.procurement.supplierquote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.rfq.Rfq;
import com.mfg.ims.procurement.rfq.RfqItem;
import com.mfg.ims.procurement.rfq.RfqRepository;
import com.mfg.ims.procurement.service.PurchaseOrderService;

@Service
@Transactional
public class SupplierQuoteService {

    private final SupplierQuoteRepository repo;
    private final RfqRepository rfqRepo;
    private final PurchaseOrderService poService;

    public SupplierQuoteService(SupplierQuoteRepository repo, RfqRepository rfqRepo,
            PurchaseOrderService poService) {
        this.repo = repo;
        this.rfqRepo = rfqRepo;
        this.poService = poService;
    }

    @Transactional(readOnly = true)
    public List<SupplierQuoteDto> listByRfq(Long rfqId) {
        return repo.findByRfqIdOrderByCreatedAtDesc(rfqId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SupplierQuoteDto get(Long id) {
        return toDto(find(id));
    }

    public SupplierQuoteDto create(SaveSupplierQuoteRequest req) {
        var quote = new SupplierQuote();
        quote.setDocNo(req.docNo());
        quote.setRfqId(req.rfqId());
        quote.setSupplierId(req.supplierId());
        quote.setValidUntil(req.validUntil());
        quote.setLeadTimeDays(req.leadTimeDays());
        quote.setFreight(req.freight() != null ? req.freight() : BigDecimal.ZERO);
        quote.setNotes(req.notes());
        if (req.items() != null) {
            int lineNo = 1;
            for (var line : req.items()) {
                var item = new SupplierQuoteItem();
                item.setQuote(quote);
                item.setRfqItemId(line.rfqItemId());
                item.setLineNo(lineNo++);
                item.setUnitPrice(line.unitPrice() != null ? line.unitPrice() : BigDecimal.ZERO);
                item.setTaxPct(line.taxPct() != null ? line.taxPct() : BigDecimal.ZERO);
                item.setMoq(line.moq());
                quote.getItems().add(item);
            }
        }
        return toDto(repo.save(quote));
    }

    @Transactional(readOnly = true)
    public ComparisonDto getComparison(Long rfqId) {
        var rfq = rfqRepo.findById(rfqId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RFQ not found"));
        var quotes = repo.findByRfqIdOrderByCreatedAtDesc(rfqId);

        // Build per-rfqItem → list of bids
        Map<Long, List<ComparisonDto.SupplierBidDto>> bidsByItem = rfq.getItems().stream()
                .collect(Collectors.toMap(i -> i.getId(), i -> new ArrayList<>()));

        for (var q : quotes) {
            for (var qi : q.getItems()) {
                var bid = new ComparisonDto.SupplierBidDto(
                        q.getId(),
                        q.getSupplierId(),
                        qi.getUnitPrice(),
                        qi.getTaxPct(),
                        q.getFreight(),
                        q.getLeadTimeDays(),
                        computeLandedCost(qi.getUnitPrice(), qi.getTaxPct(), q.getFreight()),
                        false);
                bidsByItem.computeIfAbsent(qi.getRfqItemId(), k -> new ArrayList<>()).add(bid);
            }
        }

        // Mark lowest landed cost per line
        var lines = rfq.getItems().stream().map(item -> {
            var bids = bidsByItem.getOrDefault(item.getId(), List.of());
            if (!bids.isEmpty()) {
                BigDecimal minLanded = bids.stream()
                        .map(ComparisonDto.SupplierBidDto::landedCost)
                        .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
                bids = bids.stream()
                        .map(b -> new ComparisonDto.SupplierBidDto(b.quoteId(), b.supplierId(), b.unitPrice(),
                                b.taxPct(), b.freight(), b.leadTimeDays(), b.landedCost(),
                                b.landedCost().compareTo(minLanded) == 0))
                        .toList();
            }
            return new ComparisonDto.ComparisonLineDto(item.getId(), item.getLineNo(),
                    item.getDescription(), item.getQty(), bids);
        }).toList();

        return new ComparisonDto(rfqId, lines);
    }

    /**
     * Awards a supplier quote: marks it as AWARDED and creates a PurchaseOrder draft from its lines.
     * The rfqId path variable is validated to match the quote's rfqId (prevents cross-RFQ award).
     *
     * @return the id and document number of the created purchase order
     */
    public AwardResultDto award(Long rfqId, Long quoteId) {
        var quote = find(quoteId);
        if (!rfqId.equals(quote.getRfqId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote " + quoteId + " not found for RFQ " + rfqId);
        }
        if (quote.isAwarded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Quote already awarded");
        }

        // Load RFQ items to resolve materialId per line
        var rfq = rfqRepo.findById(rfqId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RFQ not found: " + rfqId));
        Map<Long, RfqItem> rfqItemsById = rfq.getItems().stream()
                .collect(java.util.stream.Collectors.toMap(RfqItem::getId, i -> i));

        // Build PO lines from quote items
        List<CreatePoRequest.PoLineRequest> lines = quote.getItems().stream()
                .map(qi -> {
                    RfqItem rfqItem = rfqItemsById.get(qi.getRfqItemId());
                    if (rfqItem == null || rfqItem.getMaterialId() == null) {
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "RFQ item " + qi.getRfqItemId() + " missing or has no material");
                    }
                    BigDecimal qty = rfqItem.getQty();
                    BigDecimal rate = qi.getUnitPrice() != null ? qi.getUnitPrice() : BigDecimal.ZERO;
                    return new CreatePoRequest.PoLineRequest(rfqItem.getMaterialId(), qty, rate, qi.getTaxPct());
                })
                .toList();

        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Quote has no line items");
        }

        var poReq = new CreatePoRequest(
                quote.getSupplierId(),
                java.time.LocalDate.now(),
                null, null,
                "Awarded from RFQ " + rfq.getDocNo() + " / Quote " + quote.getDocNo(),
                null,
                lines);
        var po = poService.create(poReq);

        // Mark quote as awarded
        quote.setAwarded(true);
        quote.setStatus("AWARDED");
        repo.save(quote);

        return new AwardResultDto(po.id(), po.poNumber());
    }

    private BigDecimal computeLandedCost(BigDecimal unitPrice, BigDecimal taxPct, BigDecimal freight) {
        if (unitPrice == null) return BigDecimal.ZERO;
        BigDecimal tax = unitPrice.multiply(taxPct != null ? taxPct : BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return unitPrice.add(tax).add(freight != null ? freight : BigDecimal.ZERO);
    }

    private SupplierQuote find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quote not found: " + id));
    }

    private SupplierQuoteDto toDto(SupplierQuote q) {
        var items = q.getItems().stream()
                .map(i -> new SupplierQuoteDto.ItemDto(i.getId(), i.getLineNo(), i.getRfqItemId(),
                        i.getUnitPrice(), i.getTaxPct(), i.getMoq(), i.getAwardedQty()))
                .toList();
        return new SupplierQuoteDto(q.getId(), q.getDocNo(), q.getStatus(), q.getRfqId(), q.getSupplierId(),
                q.getValidUntil(), q.getLeadTimeDays(), q.getFreight(), q.getNotes(), q.isAwarded(), items);
    }
}
