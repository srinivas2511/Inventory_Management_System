package com.mfg.ims.integration.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.integration.importer.ImportDtos.ImportResult;
import com.mfg.ims.support.AbstractIntegrationTest;

/**
 * BOM bulk upload (gap 13.2): verifies that the "boms" entity type creates BOM headers from CSV.
 */
class BomImportIT extends AbstractIntegrationTest {

    @Autowired BulkImportService importer;
    @Autowired JdbcTemplate jdbc;

    private String tag;
    private String productCode;

    @BeforeEach
    void setUp() {
        tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        productCode = "BITP-" + tag;

        // create a product to reference in the BOM import
        jdbc.update("""
                INSERT INTO ims.products (product_code, name, spring_type, uom, standard_cost, company_id, version)
                VALUES (?, 'BOM Import Test Product', 'COMPRESSION', 'PCS', 100, 1, 0)
                """, productCode);

        SecurityContextHolder.getContext().setAuthentication(token("DATA_IMPORT"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // cleanup in dependency order: bom_items → boms → products
        jdbc.update("DELETE FROM ims.bom_items WHERE bom_id IN (SELECT id FROM ims.boms WHERE product_id IN (SELECT id FROM ims.products WHERE product_code = ?))", productCode);
        jdbc.update("DELETE FROM ims.boms WHERE product_id IN (SELECT id FROM ims.products WHERE product_code = ?)", productCode);
        jdbc.update("DELETE FROM ims.products WHERE product_code = ?", productCode);
    }

    @Test
    void bomImportCreatesDraftBomForEachProductCode() throws Exception {
        String csv = "productCode,baseQuantity,changeReason\n"
                + productCode + ",1.000,Imported from Excel\n";

        MockMultipartFile file = new MockMultipartFile("file", "boms.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportResult result = importer.importFile("boms", file, false);

        assertThat(result.created()).as("one BOM created").isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).status()).isEqualTo("CREATED");

        Integer bomCount = jdbc.queryForObject("""
                SELECT count(*) FROM ims.boms b
                  JOIN ims.products p ON p.id = b.product_id
                 WHERE p.product_code = ? AND b.status = 'DRAFT'
                """, Integer.class, productCode);
        assertThat(bomCount).isEqualTo(1);
    }

    private static UsernamePasswordAuthenticationToken token(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                new CachedUser(9104L, "bom-import-it", true, 1, Set.of(), Set.of(authorities)), null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
