package com.springmfg.ims.qualityplus;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.springmfg.ims.common.document.DocumentLinkTarget;
import com.springmfg.ims.qualityplus.repository.CustomerComplaintRepository;

@Component
public class ComplaintDocumentLinkTarget implements DocumentLinkTarget {

    private final CustomerComplaintRepository complaints;

    public ComplaintDocumentLinkTarget(CustomerComplaintRepository complaints) {
        this.complaints = complaints;
    }

    @Override
    public String entityType() {
        return "COMPLAINT";
    }

    @Override
    public boolean exists(Long id) {
        return complaints.existsById(id);
    }

    @Override
    public Set<String> readAuthorities() {
        return Set.of("QUALITY_VIEW", "COMPLAINT_MANAGE");
    }
}
