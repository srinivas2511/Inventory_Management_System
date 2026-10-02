package com.springmfg.ims.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.AuditCommand;
import com.springmfg.ims.auth.CurrentUser;
import com.springmfg.ims.common.api.PageRequests;
import com.springmfg.ims.common.api.PageResponse;
import com.springmfg.ims.common.api.Specs;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.exception.Problems;
import com.springmfg.ims.common.exception.ValidationFailedException;
import com.springmfg.ims.masterdata.BusinessPartner;
import com.springmfg.ims.masterdata.Customer;
import com.springmfg.ims.masterdata.CustomerRepository;
import com.springmfg.ims.masterdata.Material;
import com.springmfg.ims.masterdata.MaterialRepository;
import com.springmfg.ims.masterdata.PartnerDtos.PartnerRef;

/**
 * Spring products (DESIGN.md sections 2.3, 5.2, 6.3).
 * <ul>
 * <li>A product starts as {@code DRAFT}. A draft may be saved unfinished (types, ranges and unknown keys are still
 * checked); required attributes are enforced when it is activated and whenever an ACTIVE product is edited.</li>
 * <li>Status changes only through {@link #activate} and {@link #obsolete}; editing never changes it. Obsolete
 * products cannot be edited. The spring type can only change while the product is a draft.</li>
 * <li>A primary material or customer must exist and be active when it is chosen; one chosen earlier may stay.</li>
 * <li>Every change is audited with old and new values (the specifications included).</li>
 * </ul>
 */
@Service
public class ProductService {

    private static final Set<String> SORTABLE = Set.of("code", "name", "springType", "status", "drawingNumber",
            "wireDiameter", "outerDiameter", "freeLength", "active", "createdAt");
    private static final Pattern CODE = Pattern.compile(ProductDtos.CODE_PATTERN);

    private final ProductRepository products;
    private final SpringSpecificationRepository specifications;
    private final MaterialRepository materials;
    private final CustomerRepository customers;
    private final SpringAttributeValidator validator;
    private final AttributeDefinitionRepository definitions;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    ProductService(ProductRepository products, SpringSpecificationRepository specifications, MaterialRepository materials,
            CustomerRepository customers, SpringAttributeValidator validator, AttributeDefinitionRepository definitions,
            JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.products = products;
        this.specifications = specifications;
        this.materials = materials;
        this.customers = customers;
        this.validator = validator;
        this.definitions = definitions;
        this.jdbc = jdbc;
        this.events = events;
    }

    // ---------------------------------------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.ProductSummary> list(String q, SpringType type, ProductStatus status, Boolean active,
            Long customerId, Long materialId, Pageable pageable) {
        Pageable page = PageRequests.restrictSort(pageable, SORTABLE, Sort.by("code"));
        Sort translated = Sort.by(page.getSort().stream()
                .map(o -> o.getProperty().equals("code") ? o.withProperty("productCode") : o).toList());
        var spec = Specs.<Product>of().search(q, "productCode", "name", "drawingNumber").eq("springType", type)
                .eq("status", status).eq("active", active).eq("customer.id", customerId)
                .eq("primaryMaterial.id", materialId).build();
        return PageResponse.from(products.findAll(spec, PageRequest.of(page.getPageNumber(), page.getPageSize(), translated)),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public ProductDtos.ProductResponse get(long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.SpringTypeResponse> springTypes() {
        Map<SpringType, Integer> counts = new HashMap<>();
        definitions.countByType().forEach(row -> counts.put((SpringType) row[0], ((Long) row[1]).intValue()));
        List<ProductDtos.SpringTypeResponse> result = new ArrayList<>();
        for (SpringType type : SpringType.values()) {
            result.add(new ProductDtos.SpringTypeResponse(type, type.label(), counts.getOrDefault(type, 0), type == SpringType.CUSTOM));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ProductDtos.AttributeResponse> attributes(SpringType type) {
        return validator.catalogue(type).stream().map(d -> new ProductDtos.AttributeResponse(d.getAttributeCode(), d.getLabel(),
                d.getDataType().name(), d.getUnit(), d.isRequired(), d.getMinValue(), d.getMaxValue(),
                d.getEnumValues() == null ? List.of() : d.getEnumValues(), d.getDisplayOrder(),
                SpringAttributeValidator.isCore(d) ? "CORE" : "SPECIFICATIONS")).toList();
    }

    // ---------------------------------------------------------------------------------------------- commands

    @Transactional
    public ProductDtos.ProductResponse create(ProductDtos.ProductRequest request) {
        String code = request.productCode() == null ? "" : request.productCode().trim();
        if (!CODE.matcher(code).matches()) {
            throw new ValidationFailedException("productCode", "Required: 2-30 characters, A-Z, 0-9, dot, underscore or hyphen, upper case.");
        }
        if (products.existsByProductCode(code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Product code '" + code + "' already exists.");
        }
        Product product = new Product(code);
        Map<String, Object> specs = SpringAttributeValidator.normalise(request.specifications());
        product.apply(details(request, null, specs, false));
        Product saved = products.saveAndFlush(product);
        specifications.saveAndFlush(new SpringSpecification(saved.getId(), specs));
        events.publishEvent(new AuditCommand("PRODUCT_CREATED", "Product", String.valueOf(saved.getId()), null, snapshot(saved, specs), null));
        return toResponse(saved, specs);
    }

    @Transactional
    public ProductDtos.ProductResponse update(long id, ProductDtos.ProductRequest request) {
        Product product = find(id);
        if (request.version() == null) {
            throw new ValidationFailedException("version", "The current version is required.");
        }
        if (product.getVersion() != request.version().longValue()) {
            throw new ConflictException(ErrorCode.VERSION_CONFLICT, "The product was modified by someone else. Reload and try again.");
        }
        if (request.productCode() != null && !request.productCode().trim().equals(product.getProductCode())) {
            throw new ValidationFailedException("productCode", "The product code cannot be changed.");
        }
        if (product.getStatus() == ProductStatus.OBSOLETE) {
            throw new BusinessRuleException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Product '" + product.getProductCode() + "' is obsolete and cannot be edited. Activate it first.");
        }
        if (request.springType() != product.getSpringType() && product.getStatus() != ProductStatus.DRAFT) {
            throw new ValidationFailedException("springType", "The spring type can only change while the product is a draft.");
        }
        Map<String, Object> before = snapshot(product, specificationsOf(id));
        Map<String, Object> specs = SpringAttributeValidator.normalise(request.specifications());
        product.apply(details(request, product, specs, product.getStatus() == ProductStatus.ACTIVE));
        products.saveAndFlush(product);
        specifications.saveAndFlush(new SpringSpecification(id, specs));
        events.publishEvent(new AuditCommand("PRODUCT_UPDATED", "Product", String.valueOf(id), before, snapshot(product, specs), null));
        return toResponse(product, specs);
    }

    /** DRAFT or OBSOLETE to ACTIVE. The product must be complete: every required attribute of its type present. */
    @Transactional
    public ProductDtos.ProductResponse activate(long id) {
        Product product = find(id);
        if (product.getStatus() == ProductStatus.ACTIVE) {
            return toResponse(product);
        }
        Map<String, Object> specs = specificationsOf(id);
        List<Problems.FieldError> errors = validator.validate(product.getSpringType(), core(product), specs, true);
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        Map<String, Object> before = snapshot(product, specs);
        product.changeStatus(ProductStatus.ACTIVE);
        products.saveAndFlush(product);
        events.publishEvent(new AuditCommand("PRODUCT_ACTIVATED", "Product", String.valueOf(id), before, snapshot(product, specs), null));
        return toResponse(product, specs);
    }

    /** DRAFT or ACTIVE to OBSOLETE (the product stays on record; nothing is deleted). Idempotent. */
    @Transactional
    public void obsolete(long id, String reason) {
        Product product = find(id);
        if (product.getStatus() == ProductStatus.OBSOLETE) {
            return;
        }
        Map<String, Object> specs = specificationsOf(id);
        Map<String, Object> before = snapshot(product, specs);
        product.changeStatus(ProductStatus.OBSOLETE);
        products.saveAndFlush(product);
        events.publishEvent(new AuditCommand("PRODUCT_OBSOLETED", "Product", String.valueOf(id), before, snapshot(product, specs),
                reason == null || reason.isBlank() ? null : reason.trim()));
    }

    // ---------------------------------------------------------------------------------------------- building

    private Product find(long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    private Map<String, Object> specificationsOf(long id) {
        return specifications.findById(id).map(SpringSpecification::getAttributes).orElseGet(LinkedHashMap::new);
    }

    /** Resolves references, runs every check and returns the values to store. */
    private Product.Details details(ProductDtos.ProductRequest r, Product existing, Map<String, Object> specs, boolean complete) {
        List<Problems.FieldError> errors = new ArrayList<>();
        errors.addAll(validator.validate(r.springType(), core(r), specs, complete));
        crossChecks(r, errors);

        Material material = existing == null ? null : existing.getPrimaryMaterial();
        if (r.primaryMaterialId() == null) {
            material = null;
        } else if (material == null || !material.getId().equals(r.primaryMaterialId())) {
            material = materials.findById(r.primaryMaterialId()).orElse(null);
            if (material == null) {
                errors.add(new Problems.FieldError("primaryMaterialId", "Unknown material " + r.primaryMaterialId() + "."));
            } else if (!material.isActive()) {
                errors.add(new Problems.FieldError("primaryMaterialId", "Material '" + material.getMaterialCode() + "' is inactive."));
            }
        }
        Customer customer = existing == null ? null : existing.getCustomer();
        if (r.customerId() == null) {
            customer = null;
        } else if (customer == null || !customer.getId().equals(r.customerId())) {
            customer = customers.findById(r.customerId()).orElse(null);
            if (customer == null) {
                errors.add(new Problems.FieldError("customerId", "Unknown customer " + r.customerId() + "."));
            } else if (!customer.isActive()) {
                errors.add(new Problems.FieldError("customerId", "Customer '" + customer.getCode() + "' is inactive."));
            }
        }
        String uom = r.uom() == null || r.uom().isBlank() ? "PCS" : r.uom().trim();
        Integer known = jdbc.queryForObject("SELECT count(*) FROM uoms WHERE code = ?", Integer.class, uom);
        if (known == null || known == 0) {
            errors.add(new Problems.FieldError("uom", "Unknown unit of measure '" + uom + "'."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        return new Product.Details(r.name().trim(), r.springType(), material, r.wireDiameter(), r.outerDiameter(), r.innerDiameter(),
                r.freeLength(), r.numberOfCoils(), r.activeCoils(), r.springRate(), r.maxLoad(), r.minLoad(), r.workingLength(),
                r.solidHeight(), blank(r.endType()), blank(r.surfaceTreatment()), blank(r.heatTreatment()), blank(r.tolerance()),
                r.unitWeightKg(), uom, blank(r.drawingNumber()), blank(r.drawingRevision()), customer,
                r.reorderLevel() == null ? BigDecimal.ZERO : r.reorderLevel(), r.standardCost() == null ? BigDecimal.ZERO : r.standardCost());
    }

    /** Physical sanity rules that hold for every spring type. */
    private static void crossChecks(ProductDtos.ProductRequest r, List<Problems.FieldError> errors) {
        if (r.innerDiameter() != null && r.outerDiameter() != null && r.innerDiameter().compareTo(r.outerDiameter()) >= 0) {
            errors.add(new Problems.FieldError("innerDiameter", "Must be smaller than the outer diameter."));
        }
        if (r.activeCoils() != null && r.numberOfCoils() != null && r.activeCoils().compareTo(r.numberOfCoils()) > 0) {
            errors.add(new Problems.FieldError("activeCoils", "Cannot exceed the number of coils."));
        }
        if (r.minLoad() != null && r.maxLoad() != null && r.minLoad().compareTo(r.maxLoad()) > 0) {
            errors.add(new Problems.FieldError("minLoad", "Cannot exceed the max load."));
        }
    }

    private static Map<String, Object> core(ProductDtos.ProductRequest r) {
        Map<String, Object> core = new HashMap<>();
        core.put("wireDiameter", r.wireDiameter());
        core.put("outerDiameter", r.outerDiameter());
        core.put("innerDiameter", r.innerDiameter());
        core.put("freeLength", r.freeLength());
        core.put("numberOfCoils", r.numberOfCoils());
        core.put("activeCoils", r.activeCoils());
        core.put("springRate", r.springRate());
        core.put("maxLoad", r.maxLoad());
        core.put("minLoad", r.minLoad());
        core.put("workingLength", r.workingLength());
        core.put("solidHeight", r.solidHeight());
        core.put("endType", blank(r.endType()));
        return core;
    }

    private static Map<String, Object> core(Product p) {
        Map<String, Object> core = new HashMap<>();
        core.put("wireDiameter", p.getWireDiameter());
        core.put("outerDiameter", p.getOuterDiameter());
        core.put("innerDiameter", p.getInnerDiameter());
        core.put("freeLength", p.getFreeLength());
        core.put("numberOfCoils", p.getNumberOfCoils());
        core.put("activeCoils", p.getActiveCoils());
        core.put("springRate", p.getSpringRate());
        core.put("maxLoad", p.getMaxLoad());
        core.put("minLoad", p.getMinLoad());
        core.put("workingLength", p.getWorkingLength());
        core.put("solidHeight", p.getSolidHeight());
        core.put("endType", p.getEndType());
        return core;
    }

    // ---------------------------------------------------------------------------------------------- responses

    private ProductDtos.ProductSummary toSummary(Product p) {
        return new ProductDtos.ProductSummary(p.getId(), p.getProductCode(), p.getName(), p.getSpringType(), materialRef(p.getPrimaryMaterial()),
                p.getWireDiameter(), p.getOuterDiameter(), p.getFreeLength(), p.getDrawingNumber(), p.getDrawingRevision(),
                partnerRef(p.getCustomer()), p.getStatus(), p.isActive(), allowedActions(p));
    }

    private ProductDtos.ProductResponse toResponse(Product p) {
        return toResponse(p, specificationsOf(p.getId()));
    }

    private ProductDtos.ProductResponse toResponse(Product p, Map<String, Object> specs) {
        return new ProductDtos.ProductResponse(p.getId(), p.getProductCode(), p.getName(), p.getSpringType(), materialRef(p.getPrimaryMaterial()),
                p.getWireDiameter(), p.getOuterDiameter(), p.getInnerDiameter(), p.getFreeLength(), p.getNumberOfCoils(), p.getActiveCoils(),
                p.getSpringRate(), p.getMaxLoad(), p.getMinLoad(), p.getWorkingLength(), p.getSolidHeight(), p.getEndType(),
                p.getSurfaceTreatment(), p.getHeatTreatment(), p.getTolerance(), p.getUnitWeightKg(), p.getUom(), p.getDrawingNumber(),
                p.getDrawingRevision(), partnerRef(p.getCustomer()), p.getStatus(), p.getReorderLevel(), p.getStandardCost(), p.isActive(),
                specs, allowedActions(p), p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }

    /** What the caller may do now: the lifecycle state machine intersected with their permissions (DESIGN.md 6.4). */
    private static List<String> allowedActions(Product p) {
        if (!CurrentUser.hasAuthority("PRODUCT_UPDATE")) {
            return List.of();
        }
        return switch (p.getStatus()) {
            case DRAFT -> List.of("EDIT", "ACTIVATE");
            case ACTIVE -> List.of("EDIT", "OBSOLETE");
            case OBSOLETE -> List.of("ACTIVATE");
        };
    }

    private static ProductDtos.MaterialRef materialRef(Material m) {
        return m == null ? null : new ProductDtos.MaterialRef(m.getId(), m.getMaterialCode(), m.getName());
    }

    private static PartnerRef partnerRef(BusinessPartner c) {
        return c == null ? null : new PartnerRef(c.getId(), c.getCode(), c.getName());
    }

    private static Map<String, Object> snapshot(Product p, Map<String, Object> specs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", p.getProductCode());
        map.put("name", p.getName());
        map.put("springType", p.getSpringType().name());
        map.put("status", p.getStatus().name());
        map.put("primaryMaterial", p.getPrimaryMaterial() == null ? null : p.getPrimaryMaterial().getMaterialCode());
        map.put("customer", p.getCustomer() == null ? null : p.getCustomer().getCode());
        map.put("wireDiameter", p.getWireDiameter());
        map.put("outerDiameter", p.getOuterDiameter());
        map.put("innerDiameter", p.getInnerDiameter());
        map.put("freeLength", p.getFreeLength());
        map.put("numberOfCoils", p.getNumberOfCoils());
        map.put("activeCoils", p.getActiveCoils());
        map.put("springRate", p.getSpringRate());
        map.put("maxLoad", p.getMaxLoad());
        map.put("minLoad", p.getMinLoad());
        map.put("workingLength", p.getWorkingLength());
        map.put("solidHeight", p.getSolidHeight());
        map.put("endType", p.getEndType());
        map.put("surfaceTreatment", p.getSurfaceTreatment());
        map.put("heatTreatment", p.getHeatTreatment());
        map.put("tolerance", p.getTolerance());
        map.put("unitWeightKg", p.getUnitWeightKg());
        map.put("uom", p.getUom());
        map.put("drawingNumber", p.getDrawingNumber());
        map.put("drawingRevision", p.getDrawingRevision());
        map.put("reorderLevel", p.getReorderLevel());
        map.put("standardCost", p.getStandardCost());
        map.put("specifications", new LinkedHashMap<>(specs));
        return map;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
