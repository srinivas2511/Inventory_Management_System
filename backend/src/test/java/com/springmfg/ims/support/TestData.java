package com.springmfg.ims.support;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;

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
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** A second real user id for steps that segregation of duties requires a different person to perform. */
    public static final long SECOND_USER_ID = 7_900_001L;

    /**
     * Runs one workflow step as a second, different user holding the given authorities, then restores the caller's
     * authentication. Use it for the approval step of a document the test's own user prepared or submitted.
     */
    public <T> T asSecondUser(java.util.function.Supplier<T> step, String... authorities) {
        return asUser(SECOND_USER_ID, step, authorities);
    }

    /** Runs one step as the given user id (no database row needed) holding the given authorities, then restores. */
    public <T> T asUser(long userId, java.util.function.Supplier<T> step, String... authorities) {
        Authentication before = SecurityContextHolder.getContext().getAuthentication();
        try {
            Set<String> codes = Set.copyOf(Arrays.asList(authorities));
            CachedUser user = new CachedUser(userId, "user." + userId, true, 0, Set.of(), codes);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                    codes.stream().map(SimpleGrantedAuthority::new).toList()));
            return step.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(before);
        }
    }

    /** Same as {@link #asSecondUser(java.util.function.Supplier, String...)} for a step that returns nothing. */
    public void runAsSecondUser(Runnable step, String... authorities) {
        asSecondUser(() -> {
            step.run();
            return null;
        }, authorities);
    }

    /**
     * Three open periods in 2012 (JAN, FEB, MAR) with a USD rate each, so currency tests do not depend on the date they
     * run on. Idempotent: the rows are created once per database and the rates are set every call. Dates: invoice in
     * 2012-01-15, settle in 2012-02-10 (rate janRate to febRate) or 2012-03-05.
     */
    public void foreignCurrencyPeriods(String janRate, String febRate, String marRate) {
        String[][] months = {{"FX-JAN-2012", "1", "2012-01-01", "2012-01-31"},
                {"FX-FEB-2012", "2", "2012-02-01", "2012-02-29"},
                {"FX-MAR-2012", "3", "2012-03-01", "2012-03-31"}};
        String[] rates = {janRate, febRate, marRate};
        for (int i = 0; i < months.length; i++) {
            jdbc.update("""
                    INSERT INTO ims.fiscal_periods (period_name, fiscal_year, period_number, start_date, end_date, status, company_id)
                    VALUES (?, 2011, ?, ?::date, ?::date, 'OPEN', 1)
                    ON CONFLICT DO NOTHING
                    """, months[i][0], Short.parseShort(months[i][1]) + 20, months[i][2], months[i][3]);
            jdbc.update("""
                    INSERT INTO ims.exchange_rates (from_currency, to_currency, period_id, rate, company_id)
                    SELECT 'USD', 'INR', id, ?::numeric, 1 FROM ims.fiscal_periods WHERE period_name = ?
                    ON CONFLICT (company_id, from_currency, to_currency, period_id) DO UPDATE SET rate = EXCLUDED.rate
                    """, rates[i], months[i][0]);
        }
    }

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
