package com.springmfg.ims.support;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.springmfg.ims.engineering.bom.Bom;
import com.springmfg.ims.engineering.bom.BomRepository;
import com.springmfg.ims.material.domain.Material;
import com.springmfg.ims.material.repository.MaterialRepository;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.partner.domain.Supplier;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.partner.repository.SupplierRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.product.repository.ProductRepository;

/**
 * Master-data fixtures for integration tests. A fresh Testcontainers database holds only what the Flyway
 * migrations seed (UOMs, warehouses and locations, operations, rejection reasons, GL chart, fiscal periods);
 * suppliers, materials, products, customers and BOMs must be created by the test.
 * Codes are unique per call and short enough for the narrowest code columns (VARCHAR(20)).
 */
@Component
public class TestData {

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100_000_000L);

    @Autowired SupplierRepository supplierRepo;
    @Autowired MaterialRepository materialRepo;
    @Autowired ProductRepository productRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired BomRepository bomRepo;

    public String code(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    public Supplier supplier() {
        Supplier s = new Supplier();
        s.setSupplierCode(code("S"));
        s.setName("IT Supplier");
        s.setStatus("ACTIVE");
        return supplierRepo.save(s);
    }

    public Customer customer() {
        Customer c = new Customer();
        c.setCustomerCode(code("C"));
        c.setName("IT Customer");
        c.setStatus("ACTIVE");
        return customerRepo.save(c);
    }

    public Material material() {
        Material m = new Material();
        m.setMaterialCode(code("MAT"));
        m.setName("IT Spring Steel Wire");
        m.setMaterialType("SPRING_STEEL");
        m.setUom("KG");
        m.setMinStock(BigDecimal.ZERO);
        m.setReorderLevel(BigDecimal.ZERO);
        m.setStandardCost(new BigDecimal("50.0000"));
        m.setActive(true);
        return materialRepo.save(m);
    }

    public Product product() {
        Product p = new Product();
        p.setProductCode(code("PRD"));
        p.setName("IT Compression Spring");
        p.setSpringType("COMPRESSION");
        return productRepo.save(p);
    }

    public Bom draftBom(Long productId) {
        Bom b = new Bom();
        b.setBomNumber(code("BOM"));
        b.setProductId(productId);
        b.setStatus("DRAFT");
        b.setBaseQuantity(BigDecimal.ONE);
        return bomRepo.save(b);
    }
}
