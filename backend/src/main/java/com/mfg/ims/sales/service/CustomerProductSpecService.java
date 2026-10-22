package com.mfg.ims.sales.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.product.repository.ProductRepository;
import com.mfg.ims.sales.domain.CustomerProductSpec;
import com.mfg.ims.sales.dto.CustomerProductSpecDto;
import com.mfg.ims.sales.dto.SaveCustomerProductSpecRequest;
import com.mfg.ims.sales.repository.CustomerProductSpecRepository;

@Service
@Transactional
public class CustomerProductSpecService {

    private final CustomerProductSpecRepository specRepo;
    private final ProductRepository productRepo;

    public CustomerProductSpecService(CustomerProductSpecRepository specRepo,
                                      ProductRepository productRepo) {
        this.specRepo = specRepo;
        this.productRepo = productRepo;
    }

    public CustomerProductSpecDto save(Long customerId, Long productId, SaveCustomerProductSpecRequest req) {
        productRepo.findById(productId).orElseThrow(() -> new NotFoundException("Product", productId));

        CustomerProductSpec spec = specRepo.findByCustomerIdAndProductId(customerId, productId)
                .orElseGet(() -> {
                    CustomerProductSpec s = new CustomerProductSpec();
                    s.setCustomerId(customerId);
                    s.setProductId(productId);
                    return s;
                });

        spec.setSpecReference(req.specReference());
        spec.setDrawingNumber(req.drawingNumber());
        spec.setCustomerPartNumber(req.customerPartNumber());
        spec.setNotes(req.notes());
        spec.setActive(true);
        spec = specRepo.save(spec);
        return toDto(spec);
    }

    @Transactional(readOnly = true)
    public List<CustomerProductSpecDto> findByCustomer(Long customerId) {
        return specRepo.findByCustomerIdAndActiveTrue(customerId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CustomerProductSpecDto findByCustomerAndProduct(Long customerId, Long productId) {
        return specRepo.findByCustomerIdAndProductId(customerId, productId)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("CustomerProductSpec", customerId + "/" + productId));
    }

    public void deactivate(Long customerId, Long productId) {
        specRepo.findByCustomerIdAndProductId(customerId, productId).ifPresent(spec -> {
            spec.setActive(false);
            specRepo.save(spec);
        });
    }

    private CustomerProductSpecDto toDto(CustomerProductSpec spec) {
        String code = productRepo.findById(spec.getProductId()).map(p -> p.getProductCode()).orElse("");
        String name = productRepo.findById(spec.getProductId()).map(p -> p.getName()).orElse("");
        return new CustomerProductSpecDto(
                spec.getId(), spec.getCustomerId(), spec.getProductId(),
                code, name,
                spec.getSpecReference(), spec.getDrawingNumber(),
                spec.getCustomerPartNumber(), spec.getNotes(), spec.isActive());
    }
}
