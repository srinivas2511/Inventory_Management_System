package com.mfg.ims.qualityplus;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.mfg.ims.common.document.DocumentLinkTarget;
import com.mfg.ims.qualityplus.repository.CapaRecordRepository;

@Component
public class CapaDocumentLinkTarget implements DocumentLinkTarget {

    private final CapaRecordRepository capas;

    public CapaDocumentLinkTarget(CapaRecordRepository capas) {
        this.capas = capas;
    }

    @Override
    public String entityType() {
        return "CAPA";
    }

    @Override
    public boolean exists(Long id) {
        return capas.existsById(id);
    }

    @Override
    public Set<String> readAuthorities() {
        return Set.of("QUALITY_VIEW", "CAPA_MANAGE");
    }
}
