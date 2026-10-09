package com.springmfg.ims.sales.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.partner.domain.CustomerApprovedProduct;
import com.springmfg.ims.partner.dto.ApprovedItemDto;
import com.springmfg.ims.partner.repository.CustomerApprovedProductRepository;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;

/**
 * MDM-021 / GC-2: the products a customer is approved to buy. Maintaining the list is always possible; whether a sales
 * order is held to it is the system setting {@code sales.enforce_approved_products} (off by default).
 */
@Service
@Transactional
public class CustomerApprovedProductService {

    public static final String ENFORCE_SETTING = "sales.enforce_approved_products";

    private final CustomerApprovedProductRepository repo;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;
    private final SystemSettingService settings;
    private final AuditService auditService;

    public CustomerApprovedProductService(CustomerApprovedProductRepository repo, CustomerRepository customerRepo,
                                          ProductRepository productRepo, SystemSettingService settings,
                                          AuditService auditService) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
        this.settings = settings;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ApprovedItemDto> list(Long customerId) {
        requireCustomer(customerId);
        return repo.findByCustomerId(customerId).stream()
                .map(a -> productRepo.findById(a.getProductId()).orElse(null))
                .filter(p -> p != null)
                .map(p -> new ApprovedItemDto(p.getId(), p.getProductCode(), p.getName()))
                .sorted(Comparator.comparing(ApprovedItemDto::code))
                .toList();
    }

    /** Replaces the customer's list with exactly these products. */
    public List<ApprovedItemDto> replace(Long customerId, List<Long> productIds) {
        requireCustomer(customerId);
        Set<Long> wanted = new HashSet<>(productIds);
        for (Long id : wanted) {
            Product p = productRepo.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
            if (!p.isActive()) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Product " + p.getProductCode() + " is inactive and cannot be approved");
            }
        }
        List<CustomerApprovedProduct> current = repo.findByCustomerId(customerId);
        Set<Long> before = current.stream().map(CustomerApprovedProduct::getProductId).collect(Collectors.toSet());
        for (CustomerApprovedProduct a : current) {
            if (!wanted.contains(a.getProductId())) {
                repo.delete(a);
            }
        }
        repo.flush();
        for (Long id : wanted) {
            if (!before.contains(id)) {
                repo.save(new CustomerApprovedProduct(customerId, id));
            }
        }
        auditService.record("CUSTOMER_APPROVED_PRODUCTS_SET", "CUSTOMER", String.valueOf(customerId),
                before.stream().sorted().toList().toString(), wanted.stream().sorted().toList().toString(), null);
        return list(customerId);
    }

    /** Called when a sales order line is entered: refuses a product the customer is not approved for. */
    @Transactional(readOnly = true)
    public void assertMayBuy(Long customerId, Long productId) {
        if (!settings.getBoolean(ENFORCE_SETTING, false)) {
            return;
        }
        if (!repo.existsByCustomerIdAndProductId(customerId, productId)) {
            String code = productRepo.findById(productId).map(Product::getProductCode)
                    .orElse(String.valueOf(productId));
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                    "Product " + code + " is not on this customer's approved products list");
        }
    }

    private void requireCustomer(Long customerId) {
        customerRepo.findById(customerId).orElseThrow(() -> new NotFoundException("Customer", customerId));
    }
}
