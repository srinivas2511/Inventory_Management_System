package com.mfg.ims.integration.tally;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.support.AbstractIntegrationTest;

/** Tally XML built from real journals, then parsed back so the structure and the signs are checked. */
class TallyIT extends AbstractIntegrationTest {

    @Autowired TallyExportService tally;
    @Autowired JdbcTemplate jdbc;

    private LocalDate day;
    private long periodId;
    private String narration;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(token("TALLY_EXPORT"));
        // a date nothing else in the shared database uses
        day = LocalDate.of(2031, 1, 1).plusDays(Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 3000));
        periodId = jdbc.queryForObject("SELECT id FROM ims.fiscal_periods ORDER BY start_date LIMIT 1", Long.class);
        narration = "R&D \"sample\" <batch> it's";
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static UsernamePasswordAuthenticationToken token(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                new CachedUser(9105L, "tally-it", true, 1, Set.of(), Set.of(authorities)), null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }

    private String journal(String status, String drCode, String crCode, String amount) {
        String number = "IT-T-" + UUID.randomUUID().toString().substring(0, 12);
        long id = jdbc.queryForObject("""
                INSERT INTO ims.journal_headers (journal_number, journal_type, posting_date, period_id, description,
                       prepared_by, status)
                VALUES (?, 'GL', ?, ?, ?, 1, ?) RETURNING id""", Long.class, number, day, periodId, narration, status);
        for (Object[] line : new Object[][] {{1, drCode, amount, "0"}, {2, crCode, "0", amount}}) {
            jdbc.update("""
                    INSERT INTO ims.journal_lines (journal_header_id, line_number, gl_account_id, debit_amount, credit_amount)
                    VALUES (?, ?, (SELECT id FROM ims.gl_accounts WHERE account_code = ?), ?, ?)""",
                    id, line[0], line[1], new BigDecimal((String) line[2]),
                    new BigDecimal((String) line[3]));
        }
        return number;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setNamespaceAware(false);
        return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String text(Document doc, String xpath) throws Exception {
        XPath x = XPathFactory.newInstance().newXPath();
        return (String) x.evaluate(xpath, doc, XPathConstants.STRING);
    }

    private static NodeList nodes(Document doc, String xpath) throws Exception {
        return (NodeList) XPathFactory.newInstance().newXPath().evaluate(xpath, doc, XPathConstants.NODESET);
    }

    @Test
    void vouchersCarryTheJournalWithDebitsNegativeAndCreditsPositive() throws Exception {
        String posted = journal("POSTED", "1201", "4001", "1234.5");
        journal("DRAFT", "1201", "4001", "999999");
        String reversed = journal("REVERSED", "5001", "1410", "50");

        Document doc = parse(tally.vouchers(day, day));

        assertThat(text(doc, "/ENVELOPE/HEADER/TALLYREQUEST")).isEqualTo("Import Data");
        assertThat(text(doc, "//REPORTNAME")).isEqualTo("Vouchers");
        assertThat(text(doc, "//SVCURRENTCOMPANY")).isNotBlank();
        assertThat(nodes(doc, "//VOUCHER").getLength()).as("posted and reversed journals, never a draft").isEqualTo(2);

        String v = "//VOUCHER[VOUCHERNUMBER='" + posted + "']";
        assertThat(text(doc, v + "/@VCHTYPE")).isEqualTo("Journal");
        assertThat(text(doc, v + "/DATE")).isEqualTo(day.toString().replace("-", ""));
        assertThat(text(doc, v + "/NARRATION")).as("escaped in the XML, identical once parsed").isEqualTo(narration);
        assertThat(text(doc, v + "/ALLLEDGERENTRIES.LIST[1]/LEDGERNAME")).isEqualTo("1201 Main Bank Account");
        assertThat(text(doc, v + "/ALLLEDGERENTRIES.LIST[1]/ISDEEMEDPOSITIVE")).isEqualTo("Yes");
        assertThat(text(doc, v + "/ALLLEDGERENTRIES.LIST[1]/AMOUNT")).isEqualTo("-1234.50");
        assertThat(text(doc, v + "/ALLLEDGERENTRIES.LIST[2]/LEDGERNAME")).isEqualTo("4001 Sales Revenue");
        assertThat(text(doc, v + "/ALLLEDGERENTRIES.LIST[2]/ISDEEMEDPOSITIVE")).isEqualTo("No");
        assertThat(text(doc, v + "/ALLLEDGERENTRIES.LIST[2]/AMOUNT")).isEqualTo("1234.50");
        assertThat(new BigDecimal(text(doc, "sum(" + v + "/ALLLEDGERENTRIES.LIST/AMOUNT)")))
                .as("a voucher balances").isEqualByComparingTo("0");
        assertThat(text(doc, "//VOUCHER[VOUCHERNUMBER='" + reversed + "']/DATE")).isNotBlank();
    }

    @Test
    void ledgersListEveryActiveAccountUnderItsTallyGroup() throws Exception {
        Document doc = parse(tally.ledgers());
        assertThat(text(doc, "//REPORTNAME")).isEqualTo("All Masters");
        assertThat(text(doc, "//LEDGER[@NAME='1101 Accounts Receivable Control']/PARENT")).isEqualTo("Current Assets");
        assertThat(text(doc, "//LEDGER[@NAME='4001 Sales Revenue']/PARENT")).isEqualTo("Sales Accounts");
        assertThat(text(doc, "//LEDGER[@NAME='5001 Cost of Goods Sold']/PARENT")).isEqualTo("Direct Expenses");
        Integer active = jdbc.queryForObject("SELECT count(*) FROM ims.gl_accounts WHERE active = TRUE", Integer.class);
        assertThat(nodes(doc, "//LEDGER").getLength()).isEqualTo(active);
    }

    @Test
    void aPeriodWithoutJournalsIsValidEmptyXmlAndAReversedRangeIsRefused() throws Exception {
        Document doc = parse(tally.vouchers(LocalDate.of(1999, 1, 1), LocalDate.of(1999, 1, 31)));
        assertThat(nodes(doc, "//VOUCHER").getLength()).isZero();
        assertThatThrownBy(() -> tally.vouchers(day.plusDays(1), day)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void theEndpointDownloadsXmlIsAuditedAndNeedsTallyExport() throws Exception {
        journal("POSTED", "1201", "4001", "10");
        Integer before = jdbc.queryForObject("SELECT count(*) FROM ims.audit_logs WHERE action = 'TALLY_EXPORT' "
                + "AND entity = 'TALLY_VOUCHERS'", Integer.class);

        mockMvc.perform(get("/api/integration/tally/vouchers").param("from", day.toString()).param("to", day.toString())
                        .with(authentication(token("TALLY_EXPORT"))))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/xml")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("tally-vouchers.xml")));
        mockMvc.perform(get("/api/integration/tally/ledgers").with(authentication(token("TALLY_EXPORT"))))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM ims.audit_logs WHERE action = 'TALLY_EXPORT' "
                + "AND entity = 'TALLY_VOUCHERS'", Integer.class)).isEqualTo(before + 1);
        mockMvc.perform(get("/api/integration/tally/vouchers").with(authentication(token("DATA_EXPORT"))))
                .andExpect(status().isForbidden());
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
        mockMvc.perform(get("/api/integration/tally/ledgers")).andExpect(status().isUnauthorized());
    }
}
