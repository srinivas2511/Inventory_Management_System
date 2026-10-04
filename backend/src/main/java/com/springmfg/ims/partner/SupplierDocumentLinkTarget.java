package com.springmfg.ims.partner;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.springmfg.ims.common.document.DocumentLinkTarget;
import com.springmfg.ims.partner.repository.SupplierRepository;

@Component
public class SupplierDocumentLinkTarget implements DocumentLinkTarget {

    private final SupplierRepository suppliers;

    public SupplierDocumentLinkTarget(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Override
    public String entityType() {
        return "SUPPLIER";
    }

    @Override
    public boolean exists(Long id) {
        return suppliers.existsById(id);
    }

    @Override
    public Set<String> readAuthorities() {
        return Set.of("SUPPLIER_MANAGE", "MASTERDATA_MANAGE");
    }
}
