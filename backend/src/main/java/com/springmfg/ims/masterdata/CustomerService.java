package com.springmfg.ims.masterdata;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.api.Specs;

@Service
public class CustomerService extends PartnerService<Customer> {

    static final int LOOKUP_LIMIT = 50;

    private final CustomerRepository repository;
    private final MasterDataMapper refMapper;

    CustomerService(CustomerRepository repository, MasterDataMapper mapper, ApplicationEventPublisher events) {
        super(mapper, events, "Customer", "customerCode");
        this.repository = repository;
        this.refMapper = mapper;
    }

    @Override
    JpaRepository<Customer, Long> repository() {
        return repository;
    }

    @Override
    JpaSpecificationExecutor<Customer> specifications() {
        return repository;
    }

    @Override
    boolean codeExists(String code) {
        return repository.existsByCustomerCode(code);
    }

    @Override
    Customer newEntity(String code) {
        return new Customer(code);
    }

    /** Active customers for a dropdown: code and name only, so a picker needs no read access to the records. */
    @Transactional(readOnly = true)
    public List<PartnerDtos.PartnerRef> lookup(String q) {
        var spec = Specs.<Customer>of().search(q, "customerCode", "name").eq("active", true).build();
        return repository.findAll(spec, PageRequest.of(0, LOOKUP_LIMIT, Sort.by("name"))).stream()
                .map(refMapper::toRef).toList();
    }
}
