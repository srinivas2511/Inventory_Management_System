package com.mfg.ims.plm.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.event.ProductLifecycleChangedEvent;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.plm.domain.ProductLifecycleEvent;
import com.mfg.ims.plm.domain.ProductLifecycleStatus;
import com.mfg.ims.plm.dto.LifecycleDtos.LifecycleEventDto;
import com.mfg.ims.plm.dto.LifecycleDtos.ProductLifecycleDto;
import com.mfg.ims.plm.dto.LifecycleDtos.ProductLifecycleRowDto;
import com.mfg.ims.plm.dto.LifecycleDtos.TransitionRequest;
import com.mfg.ims.plm.repository.ProductLifecycleEventRepository;
import com.mfg.ims.product.domain.Product;
import com.mfg.ims.product.repository.ProductRepository;

/** Product lifecycle (PLM-005): one-step forward transitions, each recorded and audited with a mandatory reason. */
@Service
public class ProductLifecycleService {

    private final ProductRepository productRepo;
    private final ProductLifecycleEventRepository eventRepo;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public ProductLifecycleService(ProductRepository productRepo, ProductLifecycleEventRepository eventRepo,
                                   AuditService auditService, ApplicationEventPublisher events) {
        this.productRepo = productRepo;
        this.eventRepo = eventRepo;
        this.auditService = auditService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<ProductLifecycleRowDto> list(String q, ProductLifecycleStatus lifecycle, Pageable pageable) {
        return productRepo.searchByLifecycle(q, lifecycle == null ? null : lifecycle.name(), pageable)
                .map(p -> {
                    ProductLifecycleStatus current = ProductLifecycleStatus.valueOf(p.getLifecycleStatus());
                    return new ProductLifecycleRowDto(p.getId(), p.getProductCode(), p.getName(), current,
                            current.next().orElse(null));
                });
    }

    @Transactional(readOnly = true)
    public ProductLifecycleDto get(Long productId) {
        Product p = load(productId);
        ProductLifecycleStatus current = ProductLifecycleStatus.valueOf(p.getLifecycleStatus());
        List<LifecycleEventDto> history = eventRepo.findByProductIdOrderByChangedAtDescIdDesc(productId).stream()
                .map(ProductLifecycleService::toDto).toList();
        return new ProductLifecycleDto(p.getId(), p.getProductCode(), p.getName(), current,
                current.next().orElse(null), history);
    }

    @Transactional
    public ProductLifecycleDto transition(Long productId, TransitionRequest req) {
        String reason = requireReason(req.reason());
        Product p = load(productId);
        ProductLifecycleStatus current = ProductLifecycleStatus.valueOf(p.getLifecycleStatus());
        ProductLifecycleStatus target = current.transitionTo(req.target());

        p.setLifecycleStatus(target.name());
        productRepo.save(p);

        ProductLifecycleEvent event = new ProductLifecycleEvent();
        event.setProductId(productId);
        event.setFromStatus(current.name());
        event.setToStatus(target.name());
        event.setChangedBy(PlmSecurity.currentUserId());
        event.setReason(reason);
        eventRepo.save(event);

        auditService.record("PRODUCT_LIFECYCLE_CHANGE", "Product", String.valueOf(productId),
                current.name(), target.name(), reason);
        events.publishEvent(new ProductLifecycleChangedEvent(productId, current.name(), target.name()));
        return get(productId);
    }

    /** Records that an ECO implementation touched the product; the lifecycle state itself does not change. */
    @Transactional
    public void recordEcoImplementation(Long productId, Long ecoId, String reason) {
        Product p = load(productId);
        ProductLifecycleEvent event = new ProductLifecycleEvent();
        event.setProductId(productId);
        event.setFromStatus(p.getLifecycleStatus());
        event.setToStatus(p.getLifecycleStatus());
        event.setEcoId(ecoId);
        event.setChangedBy(PlmSecurity.currentUserId());
        event.setReason(reason);
        eventRepo.save(event);
    }

    private Product load(Long productId) {
        return productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A reason is required for a lifecycle change");
        }
        return reason.trim();
    }

    private static LifecycleEventDto toDto(ProductLifecycleEvent e) {
        return new LifecycleEventDto(e.getId(), e.getProductId(), e.getFromStatus(), e.getToStatus(), e.getEcoId(),
                e.getChangedBy(), e.getChangedAt(), e.getReason());
    }
}
