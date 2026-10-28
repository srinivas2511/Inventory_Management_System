package com.mfg.ims.doccore.paymentterms;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class PaymentTermService {

    private final PaymentTermRepository repo;

    public PaymentTermService(PaymentTermRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<PaymentTermDto> list() {
        return repo.findByCompanyIdAndActiveTrueOrderByTermCode(1L)
                .stream().map(PaymentTermDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentTermDto> listAll() {
        return repo.findAll().stream().map(PaymentTermDto::from).toList();
    }

    @Transactional
    public PaymentTermDto create(SavePaymentTermRequest req) {
        if (repo.existsByCompanyIdAndTermCode(1L, req.termCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Payment term code already exists: " + req.termCode());
        }
        PaymentTerm t = new PaymentTerm();
        t.setTermCode(req.termCode().toUpperCase().trim());
        t.setName(req.name().trim());
        t.setNetDays(req.netDays());
        t.setDiscountPct(req.discountPct());
        t.setDiscountDays(req.discountDays());
        return PaymentTermDto.from(repo.save(t));
    }

    @Transactional
    public PaymentTermDto update(Long id, SavePaymentTermRequest req) {
        PaymentTerm t = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment term not found: " + id));
        t.setName(req.name().trim());
        t.setNetDays(req.netDays());
        t.setDiscountPct(req.discountPct());
        t.setDiscountDays(req.discountDays());
        return PaymentTermDto.from(repo.save(t));
    }

    @Transactional
    public void deactivate(Long id) {
        PaymentTerm t = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment term not found: " + id));
        t.setActive(false);
        repo.save(t);
    }
}
