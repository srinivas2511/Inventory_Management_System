package com.mfg.ims.integration.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.integration.importer.ImportDtos.ImportResult;
import com.mfg.ims.integration.importer.ImportDtos.RowResult;
import com.mfg.ims.support.AbstractIntegrationTest;

/** Bulk import through the real services and validation, one transaction per row. */
class ImportIT extends AbstractIntegrationTest {

    @Autowired BulkImportService importer;
    @Autowired JdbcTemplate jdbc;

    private String tag;

    @BeforeEach
    void actAsImporter() {
        tag = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        SecurityContextHolder.getContext().setAuthentication(token("DATA_IMPORT"));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static UsernamePasswordAuthenticationToken token(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                new CachedUser(9103L, "import-it", true, 1, Set.of(), Set.of(authorities)), null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private static MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private String customersCsv() {
        return "customerCode,name,contactPerson,gstNumber,paymentTerms\n"
                + "IMP-" + tag + "-1,Alpha Springs,Asha,27AABCS1234A1Z5,Net 30\n"
                + "IMP-" + tag + "-2,Beta Wire,,,Net 45\n"
                + "IMP-" + tag + "-3,Gamma Coils,Gita,29AABCG5678B1Z2,\n"
                + "IMP-" + tag + "-4,Bad GST Co,Ben,NOT-A-GST,\n"
                + "IMP-" + tag + "-5,,Cara,,\n"
                + "IMP-" + tag + "-1,Alpha Again,Asha,,\n";
    }

    private int customersWithTag() {
        return jdbc.queryForObject("SELECT count(*) FROM ims.customers WHERE customer_code LIKE ?", Integer.class,
                "IMP-" + tag + "-%");
    }

    private static RowResult row(ImportResult r, int rowNumber) {
        return r.rows().stream().filter(x -> x.rowNumber() == rowNumber).findFirst().orElseThrow();
    }

    @Test
    void aDryRunValidatesEverythingAndWritesNothing() throws Exception {
        ImportResult r = importer.importFile("customers", csv("customers.csv", customersCsv()), true);

        assertThat(r.dryRun()).isTrue();
        assertThat(r.totalRows()).isEqualTo(6);
        assertThat(r.valid()).isEqualTo(3);
        assertThat(r.created()).isZero();
        assertThat(r.failed()).isEqualTo(3);
        assertThat(row(r, 2).status()).isEqualTo("OK");
        assertThat(row(r, 3).status()).isEqualTo("OK");
        assertThat(row(r, 4).status()).isEqualTo("OK");
        assertThat(row(r, 5).message()).contains("gstNumber").contains("Invalid GST number");
        assertThat(row(r, 6).message()).contains("name");
        assertThat(row(r, 7).message()).contains("Duplicate code in the file");
        assertThat(customersWithTag()).as("nothing was written").isZero();
    }

    @Test
    void aRealRunCreatesTheGoodRowsReportsTheBadOnesAndRefusesToCreateTwice() throws Exception {
        ImportResult r = importer.importFile("customers", csv("customers.csv", customersCsv()), false);

        assertThat(r.created()).isEqualTo(3);
        assertThat(r.failed()).isEqualTo(3);
        assertThat(row(r, 2).status()).isEqualTo("CREATED");
        assertThat(customersWithTag()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT name FROM ims.customers WHERE customer_code = ?", String.class,
                "IMP-" + tag + "-2")).isEqualTo("Beta Wire");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ims.audit_logs WHERE action = 'BULK_IMPORT' "
                + "AND entity = 'IMPORT_CUSTOMERS'", Integer.class)).isGreaterThanOrEqualTo(1);

        ImportResult again = importer.importFile("customers", csv("customers.csv", customersCsv()), false);
        assertThat(again.created()).as("a code that exists is reported, never updated").isZero();
        assertThat(row(again, 2).message()).contains("Already exists");
        assertThat(customersWithTag()).isEqualTo(3);
    }

    @Test
    void referenceColumnsAcceptCodesInsteadOfIds() throws Exception {
        String supplierCode = "IMS-" + tag;
        importer.importFile("suppliers", csv("s.csv", "supplierCode,name,leadTimeDays\n" + supplierCode + ",Steel Co,14\n"), false);
        Long supplierId = jdbc.queryForObject("SELECT id FROM ims.suppliers WHERE supplier_code = ?", Long.class, supplierCode);

        String materials = "materialCode,name,materialType,uom,minStock,reorderLevel,standardCost,preferredSupplierCode\n"
                + "IMM-" + tag + "-1,Wire A,SPRING_STEEL,KG,10,20,55.5," + supplierCode + "\n"
                + "IMM-" + tag + "-2,Wire B,SPRING_STEEL,KG,10,20,60,NO-SUCH-SUPPLIER\n";
        ImportResult r = importer.importFile("materials", csv("m.csv", materials), false);

        assertThat(r.created()).isEqualTo(1);
        assertThat(row(r, 3).message()).contains("Unknown supplier code: NO-SUCH-SUPPLIER");
        assertThat(jdbc.queryForObject("SELECT preferred_supplier_id FROM ims.materials WHERE material_code = ?",
                Long.class, "IMM-" + tag + "-1")).isEqualTo(supplierId);
        assertThat(jdbc.queryForObject("SELECT standard_cost FROM ims.materials WHERE material_code = ?",
                java.math.BigDecimal.class, "IMM-" + tag + "-1")).isEqualByComparingTo("55.5");
    }

    @Test
    void productsImportWithMaterialAndCustomerCodesAndAttributes() throws Exception {
        importer.importFile("customers", csv("c.csv", "customerCode,name\nIMC-" + tag + ",Buyer Ltd\n"), false);
        importer.importFile("materials", csv("m.csv",
                "materialCode,name,materialType,uom,minStock,reorderLevel,standardCost\nIMW-" + tag + ",Wire,SPRING_STEEL,KG,0,0,50\n"),
                false);
        String products = "productCode,name,springType,primaryMaterialCode,customerCode,wireDiameter,uom,attributes\n"
                + "IMP-" + tag + "-A,Spring A,COMPRESSION,IMW-" + tag + ",IMC-" + tag + ",2.5,PCS,\"{\"\"material_grade\"\":\"\"EN10270\"\"}\"\n"
                + "IMP-" + tag + "-B,Spring B,NOT_A_TYPE,,,,,\n"
                + "IMP-" + tag + "-C,Spring C,TORSION,,,,,{not json\n";
        ImportResult r = importer.importFile("products", csv("p.csv", products), false);

        assertThat(r.created()).as("rows: %s", r.rows()).isEqualTo(1);
        assertThat(row(r, 3).message()).contains("springType");
        assertThat(row(r, 4).message()).contains("attributes is not valid JSON");
        assertThat(jdbc.queryForObject("SELECT wire_diameter FROM ims.products WHERE product_code = ?",
                java.math.BigDecimal.class, "IMP-" + tag + "-A")).isEqualByComparingTo("2.5");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ims.products p JOIN ims.materials m ON m.id = p.primary_material_id
                  JOIN ims.customers c ON c.id = p.customer_id
                 WHERE p.product_code = ? AND m.material_code = ? AND c.customer_code = ?""", Integer.class,
                "IMP-" + tag + "-A", "IMW-" + tag, "IMC-" + tag)).isEqualTo(1);
    }

    @Test
    void headersAreCaseAndUnderscoreInsensitiveButAnUnknownColumnRejectsTheFile() throws Exception {
        ImportResult ok = importer.importFile("customers",
                csv("c.csv", "customer_code,NAME,Gst_Number\nIMC-" + tag + "-X,Delta,27AABCS1234A1Z5\n"), false);
        assertThat(ok.created()).isEqualTo(1);

        assertThatThrownBy(() -> importer.importFile("customers",
                csv("c.csv", "customerCode,name,emial\nIMC-" + tag + "-Y,Echo,x@y.z\n"), false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Unknown column(s): emial");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ims.customers WHERE customer_code = ?", Integer.class,
                "IMC-" + tag + "-Y")).as("a typo in a header never silently drops data").isZero();
    }

    @Test
    void anXlsxWorkbookImportsLikeACsv() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("customers");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("customerCode");
            header.createCell(1).setCellValue("name");
            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("IMX-" + tag);
            r1.createCell(1).setCellValue("Excel Customer");
            wb.write(out);
            bytes = out.toByteArray();
        }
        ImportResult r = importer.importFile("customers", new MockMultipartFile("file", "c.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes), false);
        assertThat(r.created()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM ims.customers WHERE customer_code = ?", String.class,
                "IMX-" + tag)).isEqualTo("Excel Customer");
    }

    @Test
    void emptyFilesUnknownEntitiesAndWrongTypesAreRefused() {
        assertThatThrownBy(() -> importer.importFile("customers", csv("e.csv", ""), true))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> importer.importFile("users", csv("u.csv", "a\n1\n"), true))
                .isInstanceOf(com.mfg.ims.common.exception.NotFoundException.class);
        assertThatThrownBy(() -> importer.importFile("customers", csv("c.txt", "customerCode\nX\n"), true))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(importer.entities()).containsExactly("materials", "products", "customers", "suppliers", "opening-stock");
        assertThat(importer.template("customers")).startsWith("customerCode,name,");
    }

    @Test
    void openingStockImportCreatesInventoryTransactions() throws Exception {
        Long warehouseId = jdbc.queryForObject("SELECT id FROM ims.warehouses LIMIT 1", Long.class);
        String whCode = jdbc.queryForObject("SELECT warehouse_code FROM ims.warehouses WHERE id = ?", String.class, warehouseId);
        String locCode = jdbc.queryForObject(
                "SELECT location_code FROM ims.warehouse_locations WHERE warehouse_id = ? LIMIT 1", String.class, warehouseId);
        String matCode = jdbc.queryForObject("SELECT material_code FROM ims.materials LIMIT 1", String.class);

        String content = "itemType,itemCode,warehouseCode,locationCode,stockStatus,quantity,uom,unitCost\n"
                + "MATERIAL," + matCode + "," + whCode + "," + locCode + ",AVAILABLE,50,KG,120\n";
        long before = jdbc.queryForObject(
                "SELECT count(*) FROM ims.inventory_transactions WHERE transaction_type = 'OPENING_BALANCE'",
                Long.class);
        ImportResult r = importer.importFile("opening-stock", csv("ob.csv", content), false);
        assertThat(r.created()).isEqualTo(1);
        long after = jdbc.queryForObject(
                "SELECT count(*) FROM ims.inventory_transactions WHERE transaction_type = 'OPENING_BALANCE'",
                Long.class);
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void theEndpointDefaultsToADryRunAndNeedsDataImport() throws Exception {
        MockMultipartFile file = csv("c.csv", customersCsv());
        mockMvc.perform(multipart("/api/integration/import/customers").file(file).with(authentication(token("DATA_IMPORT"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.valid").value(3)).andExpect(jsonPath("$.created").value(0));
        assertThat(customersWithTag()).as("no dryRun parameter means a dry run").isZero();

        mockMvc.perform(multipart("/api/integration/import/customers").file(file).param("dryRun", "false")
                        .with(authentication(token("DATA_IMPORT"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(3));
        assertThat(customersWithTag()).isEqualTo(3);

        mockMvc.perform(multipart("/api/integration/import/customers").file(file).param("dryRun", "false")
                        .with(authentication(token("CUSTOMER_MANAGE", "DATA_EXPORT"))))
                .andExpect(status().isForbidden());
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mockMvc.perform(multipart("/api/integration/import/customers").file(file)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/integration/import/customers/template").with(authentication(token("DATA_IMPORT"))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.startsWith("customerCode,")));
        mockMvc.perform(get("/api/integration/import/nope/template").with(authentication(token("DATA_IMPORT"))))
                .andExpect(status().isNotFound());
    }
}
