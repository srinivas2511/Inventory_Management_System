package com.mfg.ims.engineering.bom;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.mfg.ims.common.document.DocumentLinkTarget;

@Component
public class BomDocumentLinkTarget implements DocumentLinkTarget {

    private final BomRepository boms;

    public BomDocumentLinkTarget(BomRepository boms) {
        this.boms = boms;
    }

    @Override
    public String entityType() {
        return "BOM";
    }

    @Override
    public boolean exists(Long id) {
        return boms.existsById(id);
    }

    @Override
    public Set<String> readAuthorities() {
        return Set.of("BOM_VIEW");
    }
}
