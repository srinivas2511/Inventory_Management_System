package com.springmfg.ims.mrp.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.mrp.domain.DemandForecast;
import com.springmfg.ims.mrp.dto.DemandForecastDto;
import com.springmfg.ims.mrp.dto.SaveDemandForecastRequest;
import com.springmfg.ims.mrp.repository.DemandForecastRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;

import jakarta.persistence.criteria.Predicate;

@Service
public class DemandForecastService {

    private final DemandForecastRepository repo;
    private final ProductRepository productRepo;
    private final AuditService auditService;

    public DemandForecastService(DemandForecastRepository repo, ProductRepository productRepo,
                                 AuditService auditService) {
        this.repo = repo;
        this.productRepo = productRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<DemandForecastDto> search(Long productId, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<DemandForecast> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (productId != null) p.add(cb.equal(root.get("productId"), productId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(root.get("targetDate"), from));
            if (to != null) p.add(cb.lessThanOrEqualTo(root.get("targetDate"), to));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<DemandForecast> page = repo.findAll(spec, pageable);
        Set<Long> ids = page.getContent().stream().map(DemandForecast::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = new HashMap<>();
        productRepo.findAllById(ids).forEach(p -> products.put(p.getId(), p));
        return page.map(f -> toDto(f, products.get(f.getProductId())));
    }

    @Transactional
    public DemandForecastDto create(SaveDemandForecastRequest req) {
        Product product = requireProduct(req.productId());
        if (repo.existsByProductIdAndTargetDate(req.productId(), req.targetDate())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY,
                    "A forecast already exists for " + product.getProductCode() + " on " + req.targetDate());
        }
        DemandForecast f = new DemandForecast();
        apply(f, req);
        f = repo.save(f);
        auditService.record("FORECAST_CREATE", "DemandForecast", String.valueOf(f.getId()), null,
                product.getProductCode() + " " + f.getTargetDate() + " " + f.getQuantity(), null);
        return toDto(f, product);
    }

    @Transactional
    public DemandForecastDto update(Long id, SaveDemandForecastRequest req) {
        DemandForecast f = require(id);
        Product product = requireProduct(req.productId());
        boolean keyChanged = !f.getProductId().equals(req.productId()) || !f.getTargetDate().equals(req.targetDate());
        if (keyChanged && repo.existsByProductIdAndTargetDate(req.productId(), req.targetDate())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY,
                    "A forecast already exists for " + product.getProductCode() + " on " + req.targetDate());
        }
        String old = describe(f);
        apply(f, req);
        f = repo.save(f);
        auditService.record("FORECAST_UPDATE", "DemandForecast", String.valueOf(id), old, describe(f), null);
        return toDto(f, product);
    }

    @Transactional
    public void delete(Long id) {
        DemandForecast f = require(id);
        repo.delete(f);
        auditService.record("FORECAST_DELETE", "DemandForecast", String.valueOf(id), describe(f), null, null);
    }

    private DemandForecast require(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Demand forecast not found: " + id));
    }

    private Product requireProduct(Long productId) {
        return productRepo.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
    }

    private void apply(DemandForecast f, SaveDemandForecastRequest req) {
        f.setProductId(req.productId());
        f.setTargetDate(req.targetDate());
        f.setQuantity(req.quantity());
        f.setNotes(req.notes());
    }

    private String describe(DemandForecast f) {
        return f.getProductId() + " " + f.getTargetDate() + " " + f.getQuantity();
    }

    private DemandForecastDto toDto(DemandForecast f, Product p) {
        return new DemandForecastDto(f.getId(), f.getProductId(), p == null ? null : p.getProductCode(),
                p == null ? null : p.getName(), f.getTargetDate(), f.getQuantity(), f.getNotes());
    }
}
