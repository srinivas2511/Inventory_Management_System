package com.springmfg.ims.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.domain.SpringSpecification;
import java.util.List;

import com.springmfg.ims.product.dto.ProductDto;
import com.springmfg.ims.product.dto.SaveProductRequest;
import com.springmfg.ims.product.dto.SpringAttributeDefinitionDto;
import com.springmfg.ims.product.repository.ProductRepository;
import com.springmfg.ims.product.repository.SpringAttributeDefinitionRepository;

@Service
public class ProductService {

    private final ProductRepository productRepo;
    private final MaterialRepository materialRepo;
    private final CustomerRepository customerRepo;
    private final SpringAttributeDefinitionRepository attrDefRepo;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ProductService(ProductRepository productRepo, MaterialRepository materialRepo,
                          CustomerRepository customerRepo,
                          SpringAttributeDefinitionRepository attrDefRepo,
                          AuditService auditService, ObjectMapper objectMapper) {
        this.productRepo = productRepo;
        this.materialRepo = materialRepo;
        this.customerRepo = customerRepo;
        this.attrDefRepo = attrDefRepo;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SpringAttributeDefinitionDto> getAttributeDefinitions(String springType) {
        return attrDefRepo.findBySpringTypeOrderBySortOrderAsc(springType.toUpperCase())
            .stream().map(SpringAttributeDefinitionDto::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> search(String q, String springType, String status,
                                    Boolean active, Pageable pageable) {
        return productRepo.search(q, springType, status, active, pageable).map(ProductDto::from);
    }

    @Transactional(readOnly = true)
    public ProductDto findById(Long id) {
        return ProductDto.from(require(id));
    }

    @Transactional
    public ProductDto create(SaveProductRequest req) {
        if (productRepo.existsByProductCode(req.productCode())) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Product code already exists: " + req.productCode());
        }
        Product p = new Product();
        apply(p, req);
        productRepo.save(p);

        SpringSpecification spec = new SpringSpecification(p);
        if (req.attributes() != null) {
            spec.setAttributes(toJson(req.attributes()));
        }
        p.setSpecification(spec);
        productRepo.save(p);

        auditService.record("CREATE", "PRODUCT", String.valueOf(p.getId()), null, p.getProductCode(), null);
        return ProductDto.from(p);
    }

    @Transactional
    public ProductDto update(Long id, SaveProductRequest req) {
        Product p = require(id);
        String old = p.getProductCode();
        apply(p, req);

        SpringSpecification spec = p.getSpecification();
        if (spec == null) {
            spec = new SpringSpecification(p);
            p.setSpecification(spec);
        }
        if (req.attributes() != null) {
            spec.setAttributes(toJson(req.attributes()));
        }
        productRepo.save(p);

        auditService.record("UPDATE", "PRODUCT", String.valueOf(id), old, p.getName(), null);
        return ProductDto.from(p);
    }

    @Transactional
    public ProductDto activate(Long id) {
        Product p = require(id);
        if ("ACTIVE".equals(p.getStatus())) return ProductDto.from(p);
        if ("OBSOLETE".equals(p.getStatus())) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Cannot activate an obsolete product");
        }
        p.setStatus("ACTIVE");
        productRepo.save(p);
        auditService.record("ACTIVATE", "PRODUCT", String.valueOf(id), "DRAFT", "ACTIVE", null);
        return ProductDto.from(p);
    }

    @Transactional
    public ProductDto obsolete(Long id) {
        Product p = require(id);
        if ("OBSOLETE".equals(p.getStatus())) return ProductDto.from(p);
        p.setStatus("OBSOLETE");
        p.setActive(false);
        productRepo.save(p);
        auditService.record("OBSOLETE", "PRODUCT", String.valueOf(id), p.getStatus(), "OBSOLETE", null);
        return ProductDto.from(p);
    }

    private void apply(Product p, SaveProductRequest req) {
        p.setProductCode(req.productCode());
        p.setName(req.name());
        p.setSpringType(req.springType());
        p.setWireDiameter(req.wireDiameter());
        p.setOuterDiameter(req.outerDiameter());
        p.setInnerDiameter(req.innerDiameter());
        p.setFreeLength(req.freeLength());
        p.setNumberOfCoils(req.numberOfCoils());
        p.setActiveCoils(req.activeCoils());
        p.setSpringRate(req.springRate());
        p.setMaxLoad(req.maxLoad());
        p.setMinLoad(req.minLoad());
        p.setWorkingLength(req.workingLength());
        p.setSolidHeight(req.solidHeight());
        p.setEndType(req.endType());
        p.setSurfaceTreatment(req.surfaceTreatment());
        p.setHeatTreatment(req.heatTreatment());
        p.setTolerance(req.tolerance());
        p.setUnitWeightKg(req.unitWeightKg());
        p.setUom(req.uom() != null ? req.uom() : "PCS");
        p.setDrawingNumber(req.drawingNumber());
        p.setDrawingRevision(req.drawingRevision());
        p.setReorderLevel(req.reorderLevel() != null ? req.reorderLevel() : BigDecimal.ZERO);
        p.setStandardCost(req.standardCost() != null ? req.standardCost() : BigDecimal.ZERO);

        if (req.primaryMaterialId() != null) {
            p.setPrimaryMaterial(materialRepo.findById(req.primaryMaterialId())
                .orElseThrow(() -> new NotFoundException("Material not found: " + req.primaryMaterialId())));
        }
        if (req.customerId() != null) {
            p.setCustomer(customerRepo.findById(req.customerId())
                .orElseThrow(() -> new NotFoundException("Customer not found: " + req.customerId())));
        }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    private Product require(Long id) {
        return productRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

}
