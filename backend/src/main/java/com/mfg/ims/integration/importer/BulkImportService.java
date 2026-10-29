package com.mfg.ims.integration.importer;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mfg.ims.audit.service.AuditService;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.integration.importer.ImportDtos.ImportResult;
import com.mfg.ims.integration.importer.ImportDtos.RowResult;
import com.mfg.ims.integration.importer.RowReader.SourceRow;
import com.mfg.ims.integration.importer.RowReader.Table;
import com.mfg.ims.inventory.dto.OpeningBalanceLine;
import com.mfg.ims.inventory.dto.OpeningStockPostingPort;
import com.mfg.ims.material.dto.SaveMaterialRequest;
import com.mfg.ims.material.repository.MaterialRepository;
import com.mfg.ims.material.service.MaterialService;
import com.mfg.ims.partner.dto.SaveCustomerRequest;
import com.mfg.ims.partner.dto.SaveSupplierRequest;
import com.mfg.ims.partner.repository.CustomerRepository;
import com.mfg.ims.partner.repository.SupplierRepository;
import com.mfg.ims.partner.service.CustomerService;
import com.mfg.ims.partner.service.SupplierService;
import com.mfg.ims.product.dto.SaveProductRequest;
import com.mfg.ims.product.repository.ProductRepository;
import com.mfg.ims.product.service.ProductService;
import com.mfg.ims.warehouse.repository.WarehouseLocationRepository;
import com.mfg.ims.warehouse.repository.WarehouseRepository;

/**
 * Bulk import of master data from CSV or XLSX (INT-003). Rows go through the same services, validation and audit as
 * the screens, one transaction per row, so a bad row never blocks the good ones and nothing bypasses a business rule.
 * Only creation is supported: a code that already exists is reported as an error, not updated. A dry run validates
 * everything, including duplicates against the database and inside the file, and writes nothing.
 *
 * <p>Column headers are the request field names (any case, with or without underscores). Reference columns accept a
 * code instead of an id: {@code preferredSupplierCode} (materials), {@code primaryMaterialCode} and
 * {@code customerCode} (products). An unknown column rejects the file, so a typo cannot silently drop data.
 */
@Service
public class BulkImportService {

    static final int MAX_DETAIL_ROWS = 500;

    private record Target(String entity, Class<?> request, Function<Object, String> code, Predicate<String> exists,
                          Consumer<Object> create, Map<String, String> codeColumns, List<String> example) {
    }

    /** Flat import row for opening-stock; codes are resolved to IDs in the create consumer. */
    public record OpeningStockRow(
            @jakarta.validation.constraints.NotBlank String itemType,
            String itemCode,
            @jakarta.validation.constraints.NotBlank String warehouseCode,
            @jakarta.validation.constraints.NotBlank String locationCode,
            @jakarta.validation.constraints.NotBlank String stockStatus,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive java.math.BigDecimal quantity,
            @jakarta.validation.constraints.NotBlank String uom,
            java.math.BigDecimal unitCost,
            String remarks) {
    }

    private final Map<String, Target> targets = new LinkedHashMap<>();
    private final Validator validator;
    private final ObjectMapper mapper;
    private final SystemSettingService settings;
    private final AuditService audit;
    private final TransactionTemplate perRow;
    private final MaterialRepository materials;
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final WarehouseLocationRepository locations;
    private final OpeningStockPostingPort openingStockPort;

    public BulkImportService(Validator validator, ObjectMapper mapper, SystemSettingService settings,
                             AuditService audit, PlatformTransactionManager transactionManager,
                             MaterialService materialService, SupplierService supplierService,
                             CustomerService customerService, ProductService productService,
                             MaterialRepository materials, SupplierRepository suppliers,
                             CustomerRepository customers, ProductRepository products,
                             WarehouseRepository warehouses, WarehouseLocationRepository locations,
                             OpeningStockPostingPort openingStockPort) {
        this.validator = validator;
        this.mapper = mapper;
        this.settings = settings;
        this.audit = audit;
        this.materials = materials;
        this.suppliers = suppliers;
        this.customers = customers;
        this.products = products;
        this.warehouses = warehouses;
        this.locations = locations;
        this.openingStockPort = openingStockPort;
        this.perRow = new TransactionTemplate(transactionManager);
        this.perRow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        targets.put("materials", new Target("materials", SaveMaterialRequest.class,
                r -> ((SaveMaterialRequest) r).materialCode(), materials::existsByMaterialCode,
                r -> materialService.create((SaveMaterialRequest) r),
                Map.of("preferredSupplierCode", "preferredSupplierId"),
                List.of("materialCode", "name", "materialType", "uom", "minStock", "reorderLevel", "standardCost",
                        "grade", "diameterMm", "maxStock", "shelfLifeDays", "description", "preferredSupplierCode")));
        targets.put("products", new Target("products", SaveProductRequest.class,
                r -> ((SaveProductRequest) r).productCode(), products::existsByProductCode,
                r -> productService.create((SaveProductRequest) r),
                Map.of("primaryMaterialCode", "primaryMaterialId", "customerCode", "customerId"),
                List.of("productCode", "name", "springType", "wireDiameter", "outerDiameter", "freeLength",
                        "numberOfCoils", "springRate", "uom", "reorderLevel", "standardCost", "primaryMaterialCode",
                        "customerCode", "attributes")));
        targets.put("customers", new Target("customers", SaveCustomerRequest.class,
                r -> ((SaveCustomerRequest) r).customerCode(), customers::existsByCustomerCode,
                r -> customerService.create((SaveCustomerRequest) r), Map.of(),
                List.of("customerCode", "name", "contactPerson", "phone", "email", "address", "gstNumber",
                        "paymentTerms")));
        targets.put("suppliers", new Target("suppliers", SaveSupplierRequest.class,
                r -> ((SaveSupplierRequest) r).supplierCode(), suppliers::existsBySupplierCode,
                r -> supplierService.create((SaveSupplierRequest) r), Map.of(),
                List.of("supplierCode", "name", "contactPerson", "phone", "email", "address", "gstNumber",
                        "paymentTerms", "leadTimeDays")));
        targets.put("opening-stock", new Target("opening-stock", OpeningStockRow.class,
                r -> {
                    OpeningStockRow row = (OpeningStockRow) r;
                    return row.warehouseCode() + "/" + row.locationCode() + "/" + (row.itemCode() != null ? row.itemCode() : "?");
                },
                code -> false, // allow re-import; opening balance service is idempotent per location
                r -> {
                    OpeningStockRow row = (OpeningStockRow) r;
                    Long materialId = null, productId = null;
                    if ("MATERIAL".equalsIgnoreCase(row.itemType())) {
                        materialId = materials.findByMaterialCode(row.itemCode())
                                .orElseThrow(() -> unknownCode("material", row.itemCode())).getId();
                    } else {
                        productId = products.findByProductCode(row.itemCode())
                                .orElseThrow(() -> unknownCode("product", row.itemCode())).getId();
                    }
                    Long warehouseId = warehouses.findByWarehouseCode(row.warehouseCode())
                            .orElseThrow(() -> unknownCode("warehouse", row.warehouseCode())).getId();
                    Long locationId = locations.findByWarehouseIdAndLocationCode(warehouseId, row.locationCode())
                            .orElseThrow(() -> unknownCode("location", row.locationCode())).getId();
                    openingStockPort.post(List.of(new OpeningBalanceLine(
                            row.itemType().toUpperCase(Locale.ROOT), materialId, productId, null,
                            warehouseId, locationId, row.stockStatus().toUpperCase(Locale.ROOT),
                            row.quantity(), row.uom(), row.unitCost(), row.remarks())));
                },
                Map.of(),
                List.of("itemType", "itemCode", "warehouseCode", "locationCode", "stockStatus",
                        "quantity", "uom", "unitCost", "remarks")));
    }

    public List<String> entities() {
        return List.copyOf(targets.keySet());
    }

    /** A CSV header line with the columns of the entity, for people to start from. */
    public String template(String entity) {
        return String.join(",", target(entity).example()) + "\n";
    }

    public ImportResult importFile(String entity, MultipartFile file, boolean dryRun) throws IOException {
        Target target = target(entity);
        long maxBytes = Math.max(1, settings.getInt("integration.import.max_file_mb", 5)) * 1024L * 1024L;
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The file is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleException(ErrorCode.FILE_TOO_LARGE, "The file is larger than "
                    + settings.getInt("integration.import.max_file_mb", 5) + " MB");
        }
        int maxRows = Math.max(1, settings.getInt("integration.import.max_rows", 5000));
        Table table = RowReader.read(file, maxRows);
        Map<String, String> columns = resolveColumns(target, table.headers());

        List<RowResult> results = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();
        int valid = 0;
        int created = 0;
        int failed = 0;
        for (SourceRow row : table.rows()) {
            String key = "";
            try {
                Object request = toRequest(target, columns, row);
                key = target.code().apply(request);
                if (!seenCodes.add(key.toLowerCase(Locale.ROOT))) {
                    throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY, "Duplicate code in the file: " + key);
                }
                if (target.exists().test(key)) {
                    throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY, "Already exists: " + key);
                }
                if (dryRun) {
                    valid++;
                    results.add(new RowResult(row.rowNumber(), key, "OK", null));
                } else {
                    perRow.executeWithoutResult(status -> target.create().accept(request));
                    created++;
                    results.add(new RowResult(row.rowNumber(), key, "CREATED", null));
                }
            } catch (RuntimeException e) {
                failed++;
                results.add(new RowResult(row.rowNumber(), key, "ERROR", message(e)));
            }
        }
        if (!dryRun) {
            audit.record("BULK_IMPORT", "IMPORT_" + entity.toUpperCase(Locale.ROOT), null, null,
                    mapper.writeValueAsString(Map.of("entity", entity, "rows", table.rows().size(),
                            "created", created, "failed", failed, "file", safeName(file.getOriginalFilename()))), null);
        }
        boolean truncated = results.size() > MAX_DETAIL_ROWS;
        List<RowResult> detail = truncated ? errorsFirst(results) : results;
        return new ImportResult(entity, dryRun, table.rows().size(), valid, created, failed, detail, truncated);
    }

    private Target target(String entity) {
        Target t = entity == null ? null : targets.get(entity.toLowerCase(Locale.ROOT));
        if (t == null) {
            throw new NotFoundException("Import entity", entity);
        }
        return t;
    }

    /** Maps each header to the request field it fills; an unknown header rejects the file. */
    private Map<String, String> resolveColumns(Target target, List<String> headers) {
        Map<String, String> canonical = new LinkedHashMap<>();
        for (RecordComponent c : target.request().getRecordComponents()) {
            canonical.put(norm(c.getName()), c.getName());
        }
        target.codeColumns().keySet().forEach(k -> canonical.put(norm(k), k));
        canonical.put("version", "version");
        Map<String, String> resolved = new LinkedHashMap<>();
        Set<String> unknown = new LinkedHashSet<>();
        for (String h : headers) {
            String field = canonical.get(norm(h));
            if (field == null) {
                unknown.add(h);
            } else {
                resolved.put(h, field);
            }
        }
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown column(s): "
                    + String.join(", ", unknown) + ". Expected: " + String.join(", ", target.example()));
        }
        return resolved;
    }

    private Object toRequest(Target target, Map<String, String> columns, SourceRow row) {
        Map<String, Object> values = new LinkedHashMap<>();
        row.values().forEach((header, raw) -> {
            String field = columns.get(header);
            if (field == null || raw == null || raw.isBlank() || field.equals("version")) {
                return;
            }
            String value = raw.trim();
            String idField = target.codeColumns().get(field);
            if (idField != null) {
                values.put(idField, lookupId(field, value));
            } else if (field.equals("attributes")) {
                values.put(field, parseJson(value));
            } else {
                values.put(field, value);
            }
        });
        Object request;
        try {
            request = mapper.convertValue(values, target.request());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "A value has the wrong type: " + firstLine(
                    e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            List<String> messages = new ArrayList<>();
            violations.stream().sorted((a, b) -> a.getPropertyPath().toString().compareTo(b.getPropertyPath().toString()))
                    .forEach(v -> messages.add(v.getPropertyPath() + " " + v.getMessage()));
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, String.join("; ", messages));
        }
        return request;
    }

    private Long lookupId(String codeColumn, String code) {
        return switch (codeColumn) {
            case "preferredSupplierCode", "supplierCode" -> suppliers.findBySupplierCode(code)
                    .orElseThrow(() -> unknownCode("supplier", code)).getId();
            case "primaryMaterialCode" -> materials.findByMaterialCode(code)
                    .orElseThrow(() -> unknownCode("material", code)).getId();
            case "customerCode" -> customers.findByCustomerCode(code)
                    .orElseThrow(() -> unknownCode("customer", code)).getId();
            default -> throw new IllegalStateException("Unsupported reference column " + codeColumn);
        };
    }

    private JsonNode parseJson(String text) {
        try {
            return mapper.readTree(text);
        } catch (IOException e) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "attributes is not valid JSON");
        }
    }

    private static BusinessRuleException unknownCode(String what, String code) {
        return new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Unknown " + what + " code: " + code);
    }

    private static List<RowResult> errorsFirst(List<RowResult> all) {
        List<RowResult> out = new ArrayList<>();
        all.stream().filter(r -> "ERROR".equals(r.status())).limit(MAX_DETAIL_ROWS).forEach(out::add);
        all.stream().filter(r -> !"ERROR".equals(r.status())).limit(Math.max(0, MAX_DETAIL_ROWS - out.size()))
                .forEach(out::add);
        return out;
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static String message(RuntimeException e) {
        return firstLine(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "invalid value";
        }
        String line = text.lines().findFirst().orElse("invalid value");
        return line.length() > 300 ? line.substring(0, 300) : line;
    }

    private static String safeName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }
}
