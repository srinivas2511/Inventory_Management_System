package com.springmfg.ims.partner.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.partner.dto.CustomerDto;
import com.springmfg.ims.partner.dto.SaveCustomerRequest;
import com.springmfg.ims.partner.repository.CustomerRepository;

@Service
public class CustomerService {

    private final CustomerRepository repo;
    private final AuditService auditService;

    public CustomerService(CustomerRepository repo, AuditService auditService) {
        this.repo = repo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> search(String q, String status, Pageable pageable) {
        return repo.search(q, status, pageable).map(CustomerDto::from);
    }

    @Transactional(readOnly = true)
    public CustomerDto findById(Long id) {
        return CustomerDto.from(require(id));
    }

    @Transactional
    public CustomerDto create(SaveCustomerRequest req) {
        if (repo.existsByCustomerCode(req.customerCode())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Customer code already exists: " + req.customerCode());
        }
        Customer c = new Customer();
        apply(c, req);
        repo.save(c);
        auditService.record("CREATE", "CUSTOMER", String.valueOf(c.getId()), null, c.getCustomerCode(), null);
        return CustomerDto.from(c);
    }

    @Transactional
    public CustomerDto update(Long id, SaveCustomerRequest req) {
        Customer c = require(id);
        String old = c.getName();
        apply(c, req);
        repo.save(c);
        auditService.record("UPDATE", "CUSTOMER", String.valueOf(id), old, c.getName(), null);
        return CustomerDto.from(c);
    }

    @Transactional
    public void deactivate(Long id) {
        Customer c = require(id);
        if ("INACTIVE".equals(c.getStatus())) return;
        c.setStatus("INACTIVE");
        repo.save(c);
        auditService.record("DEACTIVATE", "CUSTOMER", String.valueOf(id), "ACTIVE", "INACTIVE", null);
    }

    @Transactional
    public void reactivate(Long id) {
        Customer c = require(id);
        if ("ACTIVE".equals(c.getStatus())) return;
        c.setStatus("ACTIVE");
        repo.save(c);
        auditService.record("REACTIVATE", "CUSTOMER", String.valueOf(id), "INACTIVE", "ACTIVE", null);
    }

    private void apply(Customer c, SaveCustomerRequest req) {
        c.setCustomerCode(req.customerCode());
        c.setName(req.name());
        c.setContactPerson(req.contactPerson());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setAddress(req.address());
        c.setGstNumber(req.gstNumber());
        c.setPaymentTerms(req.paymentTerms());
    }

    private Customer require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Customer not found: " + id));
    }
}
