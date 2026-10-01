package com.springmfg.ims.masterdata;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

@Service
public class CustomerService extends PartnerService<Customer> {

    private final CustomerRepository repository;

    CustomerService(CustomerRepository repository, MasterDataMapper mapper, ApplicationEventPublisher events) {
        super(mapper, events, "Customer", "customerCode");
        this.repository = repository;
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
}
