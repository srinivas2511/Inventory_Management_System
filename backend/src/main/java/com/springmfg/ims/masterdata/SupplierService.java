package com.springmfg.ims.masterdata;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

@Service
public class SupplierService extends PartnerService<Supplier> {

    private final SupplierRepository repository;

    SupplierService(SupplierRepository repository, MasterDataMapper mapper, ApplicationEventPublisher events) {
        super(mapper, events, "Supplier", "supplierCode");
        this.repository = repository;
    }

    @Override
    JpaRepository<Supplier, Long> repository() {
        return repository;
    }

    @Override
    JpaSpecificationExecutor<Supplier> specifications() {
        return repository;
    }

    @Override
    boolean codeExists(String code) {
        return repository.existsBySupplierCode(code);
    }

    @Override
    Supplier newEntity(String code) {
        return new Supplier(code);
    }
}
