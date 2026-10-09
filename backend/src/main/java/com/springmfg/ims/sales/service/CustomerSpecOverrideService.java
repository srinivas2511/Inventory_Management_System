package com.springmfg.ims.sales.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.sales.domain.CustomerProductSpec;
import com.springmfg.ims.sales.domain.CustomerSpecOverride;
import com.springmfg.ims.sales.dto.CustomerSpecOverrideDto;
import com.springmfg.ims.sales.dto.SaveCustomerSpecOverrideRequest;
import com.springmfg.ims.sales.repository.CustomerProductSpecRepository;
import com.springmfg.ims.sales.repository.CustomerSpecOverrideRepository;

/**
 * SAL-006 / MDM-012 (GC-5): tolerance overrides of a customer's product specification, maintained per inspection
 * parameter name. They belong to the customer's product spec, so that spec has to exist first. Every change is audited
 * with the old and new limits.
 */
@Service
@Transactional
public class CustomerSpecOverrideService {

    private final CustomerProductSpecRepository specRepo;
    private final CustomerSpecOverrideRepository overrideRepo;
    private final AuditService auditService;

    public CustomerSpecOverrideService(CustomerProductSpecRepository specRepo,
                                       CustomerSpecOverrideRepository overrideRepo, AuditService auditService) {
        this.specRepo = specRepo;
        this.overrideRepo = overrideRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CustomerSpecOverrideDto> list(Long customerId, Long productId) {
        CustomerProductSpec spec = requireSpec(customerId, productId);
        return overrideRepo.findByCustomerProductSpecIdAndActiveTrueOrderByParameterName(spec.getId()).stream()
                .map(o -> toDto(o, spec)).toList();
    }

    public CustomerSpecOverrideDto save(Long customerId, Long productId, SaveCustomerSpecOverrideRequest req) {
        BigDecimal lower = req.lowerLimit();
        BigDecimal upper = req.upperLimit();
        if (lower == null && upper == null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "An override needs a lower limit, an upper limit, or both");
        }
        if (lower != null && upper != null && lower.compareTo(upper) > 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "The lower limit cannot be above the upper limit");
        }
        CustomerProductSpec spec = requireSpec(customerId, productId);
        String name = req.parameterName().trim();
        CustomerSpecOverride o = overrideRepo.findByCustomerProductSpecIdAndParameterName(spec.getId(), name)
                .orElseGet(() -> {
                    CustomerSpecOverride n = new CustomerSpecOverride();
                    n.setCustomerProductSpecId(spec.getId());
                    n.setParameterName(name);
                    return n;
                });
        String old = o.getId() == null ? null : limits(o);
        o.setLowerLimit(lower);
        o.setUpperLimit(upper);
        o.setNominal(req.nominal());
        o.setActive(true);
        o = overrideRepo.save(o);
        auditService.record("CUSTOMER_SPEC_OVERRIDE_SAVED", "CUSTOMER_SPEC_OVERRIDE", String.valueOf(o.getId()),
                old, limits(o), "Customer " + customerId + ", product " + productId + ", parameter " + name);
        return toDto(o, spec);
    }

    public void deactivate(Long customerId, Long productId, String parameterName) {
        CustomerProductSpec spec = requireSpec(customerId, productId);
        CustomerSpecOverride o = overrideRepo
                .findByCustomerProductSpecIdAndParameterNameAndActiveTrue(spec.getId(), parameterName)
                .orElseThrow(() -> new NotFoundException("CustomerSpecOverride", parameterName));
        String old = limits(o);
        o.setActive(false);
        overrideRepo.save(o);
        auditService.record("CUSTOMER_SPEC_OVERRIDE_REMOVED", "CUSTOMER_SPEC_OVERRIDE", String.valueOf(o.getId()),
                old, null, "Customer " + customerId + ", product " + productId + ", parameter " + parameterName);
    }

    private CustomerProductSpec requireSpec(Long customerId, Long productId) {
        return specRepo.findByCustomerIdAndProductId(customerId, productId)
                .filter(CustomerProductSpec::isActive)
                .orElseThrow(() -> new NotFoundException("CustomerProductSpec", customerId + "/" + productId));
    }

    private static String limits(CustomerSpecOverride o) {
        return (o.getLowerLimit() == null ? "-" : o.getLowerLimit().toPlainString()) + " .. "
                + (o.getUpperLimit() == null ? "-" : o.getUpperLimit().toPlainString());
    }

    private static CustomerSpecOverrideDto toDto(CustomerSpecOverride o, CustomerProductSpec spec) {
        return new CustomerSpecOverrideDto(o.getId(), spec.getCustomerId(), spec.getProductId(),
                o.getParameterName(), o.getLowerLimit(), o.getUpperLimit(), o.getNominal(), o.isActive());
    }
}
