package com.mfg.ims.crm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ConflictException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.crm.domain.CustomerPrice;
import com.mfg.ims.crm.dto.PriceDto;
import com.mfg.ims.crm.dto.ResolvedPriceDto;
import com.mfg.ims.crm.dto.SavePriceRequest;
import com.mfg.ims.crm.repository.CustomerPriceRepository;
import com.mfg.ims.product.domain.Product;

import jakarta.persistence.criteria.Predicate;

@Service
public class CustomerPriceService {

    private final CustomerPriceRepository repo;
    private final CrmLookups lookups;
    private final AuditService auditService;

    public CustomerPriceService(CustomerPriceRepository repo, CrmLookups lookups, AuditService auditService) {
        this.repo = repo;
        this.lookups = lookups;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<PriceDto> search(Long customerId, Long productId, Boolean active, Pageable pageable) {
        Specification<CustomerPrice> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (customerId != null) p.add(cb.equal(root.get("customerId"), customerId));
            if (productId != null) p.add(cb.equal(root.get("productId"), productId));
            if (active != null) p.add(cb.equal(root.get("active"), active));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return repo.findAll(spec, pageable).map(this::toDto);
    }

    /** The price a customer would be quoted today, or source NONE when no price list row applies. */
    @Transactional(readOnly = true)
    public ResolvedPriceDto resolveDto(Long customerId, Long productId, BigDecimal quantity, LocalDate onDate) {
        return resolve(customerId, productId, quantity, onDate)
                .map(p -> new ResolvedPriceDto(p.getUnitPrice(), p.getCustomerId() != null ? "CUSTOMER" : "STANDARD", p.getId()))
                .orElse(new ResolvedPriceDto(null, "NONE", null));
    }

    @Transactional(readOnly = true)
    public Optional<CustomerPrice> resolve(Long customerId, Long productId, BigDecimal quantity, LocalDate onDate) {
        return PriceSelector.select(repo.findApplicable(customerId, productId, quantity, onDate));
    }

    @Transactional
    public PriceDto create(SavePriceRequest req) {
        validate(req);
        assertNoDuplicate(req, null);
        CustomerPrice p = new CustomerPrice();
        apply(p, req);
        p = repo.save(p);
        auditService.record("PRICE_CREATE", "CUSTOMER_PRICE", String.valueOf(p.getId()), null, describe(p), null);
        return toDto(p);
    }

    @Transactional
    public PriceDto update(Long id, SavePriceRequest req) {
        CustomerPrice p = require(id);
        validate(req);
        assertNoDuplicate(req, id);
        String old = describe(p);
        apply(p, req);
        p = repo.save(p);
        auditService.record("PRICE_UPDATE", "CUSTOMER_PRICE", String.valueOf(id), old, describe(p), null);
        return toDto(p);
    }

    @Transactional
    public void delete(Long id) {
        CustomerPrice p = require(id);
        repo.delete(p);
        auditService.record("PRICE_DELETE", "CUSTOMER_PRICE", String.valueOf(id), describe(p), null, null);
    }

    private void validate(SavePriceRequest req) {
        lookups.requireProduct(req.productId());
        if (req.customerId() != null) lookups.requireCustomer(req.customerId());
        if (req.validTo() != null && req.validTo().isBefore(req.validFrom())) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "validTo must not be before validFrom");
        }
    }

    private void assertNoDuplicate(SavePriceRequest req, Long excludeId) {
        BigDecimal minQty = req.minQty() != null ? req.minQty() : BigDecimal.ONE;
        Specification<CustomerPrice> spec = (root, query, cb) -> cb.and(
                req.customerId() == null ? cb.isNull(root.get("customerId")) : cb.equal(root.get("customerId"), req.customerId()),
                cb.equal(root.get("productId"), req.productId()),
                cb.equal(root.get("validFrom"), req.validFrom()),
                cb.equal(root.get("minQty"), minQty));
        boolean clash = repo.findAll(spec).stream().anyMatch(p -> !p.getId().equals(excludeId));
        if (clash) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY,
                    "A price already exists for this customer, product, minimum quantity and valid-from date");
        }
    }

    private void apply(CustomerPrice p, SavePriceRequest req) {
        p.setCustomerId(req.customerId());
        p.setProductId(req.productId());
        p.setUnitPrice(req.unitPrice());
        p.setMinQty(req.minQty() != null ? req.minQty() : BigDecimal.ONE);
        p.setValidFrom(req.validFrom());
        p.setValidTo(req.validTo());
        p.setActive(req.active() == null || req.active());
    }

    private CustomerPrice require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Price", id));
    }

    private String describe(CustomerPrice p) {
        return "customer=" + p.getCustomerId() + " product=" + p.getProductId() + " price=" + p.getUnitPrice()
                + " minQty=" + p.getMinQty() + " from=" + p.getValidFrom();
    }

    private PriceDto toDto(CustomerPrice p) {
        Product product = lookups.requireProduct(p.getProductId());
        return new PriceDto(p.getId(), p.getCustomerId(), lookups.customerName(p.getCustomerId()), p.getProductId(),
                product.getProductCode(), product.getName(), p.getUnitPrice(), p.getMinQty(), p.getValidFrom(),
                p.getValidTo(), p.isActive(), p.getVersion());
    }
}
