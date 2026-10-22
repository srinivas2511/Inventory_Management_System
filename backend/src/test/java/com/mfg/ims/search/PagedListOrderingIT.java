package com.mfg.ims.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.finance.gl.dto.JournalCreateRequest;
import com.mfg.ims.finance.gl.dto.JournalLineRequest;
import com.mfg.ims.finance.gl.service.GlAccountResolver;
import com.mfg.ims.finance.gl.service.JournalService;
import com.mfg.ims.partner.domain.Customer;
import com.mfg.ims.partner.repository.CustomerRepository;
import com.mfg.ims.quality.domain.QualityInspection;
import com.mfg.ims.quality.repository.QualityInspectionRepository;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * A paged list with no ORDER BY comes back in whatever order PostgreSQL produces, so a new document can be hidden
 * behind the first page (QCI-01 failed that way) and a page can repeat or miss rows. The default sort is declared on
 * each endpoint; these tests call the endpoints WITHOUT a sort parameter and assert the order and the paging.
 *
 * <p>Deliberately not {@code @Transactional}: the rows are committed, as they are for a real client.
 */
class PagedListOrderingIT extends AbstractIntegrationTest {

    @Autowired TestData data;
    @Autowired QualityInspectionRepository inspectionRepo;
    @Autowired CustomerRepository customerRepo;
    @Autowired ObjectMapper mapper;
    @Autowired JournalService journals;
    @Autowired GlAccountResolver glAccounts;

    private JsonNode page(String path, String authority, int page, int size, String... params) throws Exception {
        var req = get(path)
                .with(user("paging-it").authorities(new SimpleGrantedAuthority(authority)))
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size));
        for (int i = 0; i + 1 < params.length; i += 2) {
            req = req.param(params[i], params[i + 1]);
        }
        String json = mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private List<Long> ids(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        page.get("content").forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private static boolean strictlyDescending(List<Long> ids) {
        return IntStream.range(1, ids.size()).allMatch(i -> ids.get(i - 1) > ids.get(i));
    }

    @Test
    @DisplayName("the inspection worklist lists the newest inspection first and pages stably (QCI-01)")
    void inspectionWorklistIsNewestFirstAndStableAcrossPages() throws Exception {
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            QualityInspection q = new QualityInspection();
            q.setInspectionNumber(data.code("QI"));
            q.setInspectionType("INCOMING");
            q.setStatus("PENDING");
            q.setInspectedQty(new BigDecimal("10.000"));
            created.add(inspectionRepo.save(q).getId());
        }
        long newest = created.get(created.size() - 1);

        // No sort parameter at all: the endpoint's own default decides.
        JsonNode first = page("/api/quality/inspections", "QUALITY_VIEW", 0, 10, "type", "INCOMING");
        List<Long> p0 = ids(first);
        assertThat(p0).as("the newest inspection is on the first page").contains(newest);
        assertThat(p0.get(0)).as("and it is the first row").isEqualTo(newest);

        List<Long> p1 = ids(page("/api/quality/inspections", "QUALITY_VIEW", 1, 10, "type", "INCOMING"));
        List<Long> p2 = ids(page("/api/quality/inspections", "QUALITY_VIEW", 2, 10, "type", "INCOMING"));
        List<Long> stitched = new ArrayList<>(p0);
        stitched.addAll(p1);
        stitched.addAll(p2);
        assertThat(stitched).as("no row repeats or goes missing across pages").doesNotHaveDuplicates();
        assertThat(strictlyDescending(stitched)).as("newest first across the whole sequence").isTrue();

        List<Long> everything = ids(page("/api/quality/inspections", "QUALITY_VIEW", 0, 1000, "type", "INCOMING"));
        assertThat(everything.subList(0, stitched.size())).as("pages equal the unpaged order").isEqualTo(stitched);
    }

    @Test
    @DisplayName("the BOM list is newest first without any sort parameter")
    void bomListIsNewestFirst() throws Exception {
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            created.add(data.draftBom(data.product().getId()).getId());
        }
        List<Long> all = ids(page("/api/boms", "BOM_VIEW", 0, 1000));
        assertThat(strictlyDescending(all)).isTrue();
        assertThat(all.get(0)).isEqualTo(created.get(created.size() - 1));
    }

    @Test
    @DisplayName("the customer master list is in code order, whatever order the rows were inserted in")
    void customerListIsInCodeOrder() throws Exception {
        String tag = data.code("ORD");
        for (String suffix : List.of("-M", "-Z", "-A", "-F")) {
            Customer c = data.customer();
            c.setCustomerCode(tag + suffix);
            customerRepo.save(c);
        }
        JsonNode all = page("/api/customers", "CUSTOMER_MANAGE", 0, 1000);
        List<String> codes = new ArrayList<>();
        all.get("content").forEach(n -> codes.add(n.get("customerCode").asText()));
        List<String> mine = codes.stream().filter(c -> c.startsWith(tag)).toList();
        assertThat(mine).as("inserted M, Z, A, F; listed in code order").containsExactly(tag + "-A", tag + "-F", tag + "-M", tag + "-Z");
    }
    @Test
    @DisplayName("the journal list is newest first with no sort parameter, so a new journal is never hidden (FIN-JNL-2)")
    void journalListIsNewestFirstAndStable() throws Exception {
        Long bank = glAccounts.idOf("1201");
        Long revenue = glAccounts.idOf("4001");
        BigDecimal value = new BigDecimal("125.0000");
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            created.add(journals.createDraft(new JournalCreateRequest("GL", LocalDate.now(),
                    "Ordering IT " + System.nanoTime(), "INR",
                    List.of(new JournalLineRequest(bank, value, BigDecimal.ZERO, "Dr bank"),
                            new JournalLineRequest(revenue, BigDecimal.ZERO, value, "Cr revenue")))).id());
        }
        List<Long> p0 = ids(page("/api/journals", "GL_VIEW", 0, 5));
        List<Long> p1 = ids(page("/api/journals", "GL_VIEW", 1, 5));
        assertThat(p0.get(0)).as("the journal created last is the first row").isEqualTo(created.get(created.size() - 1));
        List<Long> stitched = new ArrayList<>(p0);
        stitched.addAll(p1);
        assertThat(stitched).doesNotHaveDuplicates();
        assertThat(strictlyDescending(stitched)).isTrue();
        assertThat(ids(page("/api/journals", "GL_VIEW", 0, 1000)).subList(0, stitched.size())).isEqualTo(stitched);
    }

    @Test
    @DisplayName("the AP invoice, AR invoice and bank statement lists have a stable newest-first order")
    void financeListsAreNewestFirst() throws Exception {
        for (String[] endpoint : new String[][] {
                {"/api/ap/invoices", "AP_MANAGE"}, {"/api/ar/invoices", "AR_MANAGE"}, {"/api/bank/statements", "BANK_RECONCILE"} }) {
            JsonNode everything = page(endpoint[0], endpoint[1], 0, 1000);
            assertThat(strictlyDescending(ids(everything)))
                    .as(endpoint[0] + " is strictly newest first by id").isTrue();
        }
    }
}
