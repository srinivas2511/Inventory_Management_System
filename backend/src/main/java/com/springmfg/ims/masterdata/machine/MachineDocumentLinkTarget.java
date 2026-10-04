package com.springmfg.ims.masterdata.machine;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.springmfg.ims.common.document.DocumentLinkTarget;

@Component
public class MachineDocumentLinkTarget implements DocumentLinkTarget {

    private final MachineRepository machines;

    public MachineDocumentLinkTarget(MachineRepository machines) {
        this.machines = machines;
    }

    @Override
    public String entityType() {
        return "MACHINE";
    }

    @Override
    public boolean exists(Long id) {
        return machines.existsById(id);
    }

    @Override
    public Set<String> readAuthorities() {
        return Set.of("MACHINE_MANAGE", "MASTERDATA_MANAGE", "PRODUCTION_VIEW", "MAINTENANCE_VIEW");
    }
}
