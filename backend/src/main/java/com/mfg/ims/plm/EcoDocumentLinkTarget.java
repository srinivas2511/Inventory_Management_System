package com.mfg.ims.plm;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.mfg.ims.common.document.DocumentLinkTarget;
import com.mfg.ims.plm.repository.EcoRepository;

@Component
public class EcoDocumentLinkTarget implements DocumentLinkTarget {

    private final EcoRepository ecos;

    public EcoDocumentLinkTarget(EcoRepository ecos) {
        this.ecos = ecos;
    }

    @Override
    public String entityType() {
        return "ECO";
    }

    @Override
    public boolean exists(Long id) {
        return ecos.existsById(id);
    }

    @Override
    public Set<String> readAuthorities() {
        return Set.of("PLM_VIEW");
    }
}
