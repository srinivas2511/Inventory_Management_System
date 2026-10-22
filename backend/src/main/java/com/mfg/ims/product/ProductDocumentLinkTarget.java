package com.mfg.ims.product;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.mfg.ims.common.document.DocumentLinkTarget;
import com.mfg.ims.product.repository.ProductRepository;

@Component
public class ProductDocumentLinkTarget implements DocumentLinkTarget {

    private final ProductRepository products;

    public ProductDocumentLinkTarget(ProductRepository products) {
        this.products = products;
    }

    @Override
    public String entityType() {
        return "PRODUCT";
    }

    @Override
    public boolean exists(Long id) {
        return products.existsById(id);
    }

    @Override
    public Set<String> readAuthorities() {
        return Set.of("PRODUCT_VIEW");
    }
}
