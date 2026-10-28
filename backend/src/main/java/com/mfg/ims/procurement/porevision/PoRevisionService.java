package com.mfg.ims.procurement.porevision;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.procurement.domain.PurchaseOrder;
import com.mfg.ims.procurement.repository.PurchaseOrderRepository;

@Service
@Transactional
public class PoRevisionService {

    private final PoRevisionRepository revisionRepo;
    private final PurchaseOrderRepository poRepo;
    private final ObjectMapper objectMapper;

    public PoRevisionService(PoRevisionRepository revisionRepo, PurchaseOrderRepository poRepo,
            ObjectMapper objectMapper) {
        this.revisionRepo = revisionRepo;
        this.poRepo = poRepo;
        this.objectMapper = objectMapper;
    }

    public PoRevisionDto amend(Long poId, String reason) {
        var po = poRepo.findById(poId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "PO not found: " + poId));
        if (!"APPROVED".equals(po.getStatus()) && !"SENT".equals(po.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "PO must be APPROVED or SENT to amend");
        }
        int nextRevision = revisionRepo.findFirstByPoIdOrderByRevisionNoDesc(poId)
                .map(r -> r.getRevisionNo() + 1).orElse(1);
        String snapshot = toJson(po);
        var rev = new PoRevision();
        rev.setPoId(poId);
        rev.setRevisionNo(nextRevision);
        rev.setSnapshot(snapshot);
        rev.setReason(reason);
        Long userId = resolveUserId();
        rev.setCreatedBy(userId);
        return toDto(revisionRepo.save(rev));
    }

    @Transactional(readOnly = true)
    public List<PoRevisionDto> getHistory(Long poId) {
        return revisionRepo.findByPoIdOrderByRevisionNoDesc(poId).stream().map(this::toDto).toList();
    }

    private String toJson(PurchaseOrder po) {
        try {
            return objectMapper.writeValueAsString(po);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Long resolveUserId() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof com.mfg.ims.auth.service.UserPermissionCache.CachedUser u) {
                return u.id();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private PoRevisionDto toDto(PoRevision r) {
        return new PoRevisionDto(r.getId(), r.getPoId(), r.getRevisionNo(), r.getReason(),
                r.getCreatedBy(), r.getCreatedAt());
    }
}
