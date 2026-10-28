package com.mfg.ims.finance.payment;

import com.mfg.ims.common.numbering.DocumentNumberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository repo;
    private final DocumentNumberService numberService;

    public PaymentService(PaymentRepository repo, DocumentNumberService numberService) {
        this.repo = repo;
        this.numberService = numberService;
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> list() {
        return repo.findByCompanyIdOrderByDocDateDesc(1L).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PaymentDto get(Long id) {
        return toDto(find(id));
    }

    public PaymentDto create(SavePaymentRequest req, Long userId) {
        var pay = new Payment();
        pay.setDocNo(numberService.next("PAY"));
        pay.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        pay.setDirection(req.direction());
        if (req.kind() != null) pay.setKind(req.kind());
        pay.setPartyType(req.partyType());
        pay.setPartyId(req.partyId());
        pay.setMode(req.mode());
        pay.setReferenceNo(req.referenceNo());
        pay.setCurrency(req.currency() != null ? req.currency() : "INR");
        pay.setAmount(req.amount());
        BigDecimal tds = req.tdsAmount() != null ? req.tdsAmount() : BigDecimal.ZERO;
        BigDecimal charges = req.bankCharges() != null ? req.bankCharges() : BigDecimal.ZERO;
        pay.setTdsAmount(tds);
        pay.setBankCharges(charges);
        BigDecimal amountInr = req.amount().multiply(pay.getExchangeRate()).setScale(4, RoundingMode.HALF_UP);
        pay.setAmountInr(amountInr);
        pay.setUnallocatedInr(amountInr.subtract(tds).subtract(charges));
        pay.setRemarks(req.remarks());
        return toDto(repo.save(pay));
    }

    public PaymentDto post(Long id, Long userId) {
        var pay = find(id);
        if (!"DRAFT".equals(pay.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Only DRAFT payments can be posted");
        }
        pay.setStatus("POSTED");
        return toDto(repo.save(pay));
    }

    private Payment find(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Payment not found: " + id));
    }

    private PaymentDto toDto(Payment p) {
        var allocs = p.getAllocations().stream()
                .map(a -> new PaymentDto.AllocationDto(a.getId(), a.getOpenItemId(),
                        a.getAllocated(), a.getAllocatedInr(), a.getAllocatedAt()))
                .toList();
        return new PaymentDto(p.getId(), p.getDocNo(), p.getDocDate(), p.getStatus(),
                p.getDirection(), p.getKind(), p.getPartyType(), p.getPartyId(),
                p.getMode(), p.getReferenceNo(), p.getCurrency(), p.getExchangeRate(),
                p.getAmount(), p.getAmountInr(), p.getTdsAmount(), p.getBankCharges(),
                p.getUnallocatedInr(), p.getRemarks(), allocs);
    }
}
