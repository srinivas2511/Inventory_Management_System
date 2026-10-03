package com.springmfg.ims.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.crm.dto.ActivityDto;
import com.springmfg.ims.crm.dto.AdvanceOpportunityRequest;
import com.springmfg.ims.crm.dto.ConvertLeadRequest;
import com.springmfg.ims.crm.dto.ConvertLeadResultDto;
import com.springmfg.ims.crm.dto.CrmCustomerDto;
import com.springmfg.ims.crm.dto.ContactDto;
import com.springmfg.ims.crm.dto.LeadDto;
import com.springmfg.ims.crm.dto.OpportunityDto;
import com.springmfg.ims.crm.dto.QuotationDto;
import com.springmfg.ims.crm.dto.ResolvedPriceDto;
import com.springmfg.ims.crm.dto.SaveActivityRequest;
import com.springmfg.ims.crm.dto.SaveContactRequest;
import com.springmfg.ims.crm.dto.SaveLeadRequest;
import com.springmfg.ims.crm.dto.SaveOpportunityRequest;
import com.springmfg.ims.crm.dto.SavePriceRequest;
import com.springmfg.ims.crm.dto.SaveQuotationRequest;
import com.springmfg.ims.crm.dto.SaveTagRequest;
import com.springmfg.ims.crm.dto.TagDto;
import com.springmfg.ims.crm.report.CrmPipelineReport;
import com.springmfg.ims.crm.service.CrmActivityService;
import com.springmfg.ims.crm.service.CrmContactService;
import com.springmfg.ims.crm.service.CrmLeadService;
import com.springmfg.ims.crm.service.CrmOpportunityService;
import com.springmfg.ims.crm.service.CrmQuotationService;
import com.springmfg.ims.crm.service.CustomerPriceService;
import com.springmfg.ims.crm.service.CustomerTagService;
import com.springmfg.ims.insight.report.ReportRequest;
import com.springmfg.ims.partner.domain.Customer;
import com.springmfg.ims.partner.repository.CustomerRepository;
import com.springmfg.ims.product.domain.Product;
import com.springmfg.ims.sales.dto.SalesOrderDto;
import com.springmfg.ims.sales.service.SalesOrderService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * Integration tests for Release 12 - CRM (PLAN tasks R12-3.2, R12-4.1 to R12-4.4) against real PostgreSQL:
 * quotation to sales order conversion, lead to customer conversion, the per-customer activity log, customer
 * pricing, tagging, the pipeline report and the state-machine guards.
 */
@Transactional
class CrmIT extends AbstractIntegrationTest {

    @Autowired CrmLeadService leadService;
    @Autowired CrmOpportunityService opportunityService;
    @Autowired CrmQuotationService quotationService;
    @Autowired CrmActivityService activityService;
    @Autowired CrmContactService contactService;
    @Autowired CustomerPriceService priceService;
    @Autowired CustomerTagService tagService;
    @Autowired SalesOrderService salesOrderService;
    @Autowired CustomerRepository customerRepo;
    @Autowired CrmPipelineReport pipelineReport;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;
    @PersistenceContext EntityManager em;

    private final LocalDate today = LocalDate.now();

    private Long user() {
        return jdbc.queryForObject("""
                INSERT INTO ims.users (username, full_name, email, password_hash)
                VALUES (?, 'CRM Tester', ?, 'x') RETURNING id
                """, Long.class, td.code("crm"), td.code("crm") + "@example.com");
    }

    private static SaveQuotationRequest.Item line(Product p, String qty, String price, String discount) {
        return new SaveQuotationRequest.Item(p.getId(), new BigDecimal(qty),
                price == null ? null : new BigDecimal(price), discount == null ? null : new BigDecimal(discount));
    }

    private QuotationDto quotation(Customer c, Long opportunityId, SaveQuotationRequest.Item... items) {
        return quotationService.create(new SaveQuotationRequest(opportunityId, c.getId(), null, null, null,
                List.of(items), null));
    }

    private QuotationDto accepted(QuotationDto q) {
        quotationService.send(q.id());
        return quotationService.accept(q.id());
    }

    private long auditCount(String action, String entity, String entityId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ims.audit_logs WHERE action = ? AND entity = ? AND entity_id = ?",
                Long.class, action, entity, entityId);
    }

    // ── R12-3.2 quotation -> sales order ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("an accepted quotation converts to a DRAFT sales order that totals exactly the quotation")
    void quotationConvertsToSalesOrder() {
        Customer customer = td.customer();
        customer.setPaymentTerms("Net 30");
        customer.setAddress("12 Industrial Estate, Pune");
        customerRepo.saveAndFlush(customer);
        Product p1 = td.product();
        Product p2 = td.product();

        QuotationDto q = quotation(customer, null,
                line(p1, "100", "12.5000", "10"),   // net 11.25 -> 1125.00
                line(p2, "50", "20.0000", null));   // net 20.00 -> 1000.00
        assertThat(q.status()).isEqualTo("DRAFT");
        assertThat(q.quotationNumber()).startsWith("QT/");
        assertThat(q.totalAmount()).isEqualByComparingTo("2125");
        assertThat(q.paymentTerms()).isEqualTo("Net 30");

        QuotationDto sent = quotationService.send(q.id());
        assertThat(sent.status()).isEqualTo("SENT");
        assertThat(sent.sentAt()).isNotNull();
        QuotationDto accepted = quotationService.accept(q.id());
        assertThat(accepted.status()).isEqualTo("ACCEPTED");

        QuotationDto converted = quotationService.convertToSalesOrder(q.id());
        assertThat(converted.status()).isEqualTo("CONVERTED");
        assertThat(converted.convertedSalesOrderId()).isNotNull();
        assertThat(converted.convertedSalesOrderNumber()).startsWith("SO/");

        SalesOrderDto order = salesOrderService.findById(converted.convertedSalesOrderId());
        assertThat(order.status()).isEqualTo("DRAFT");
        assertThat(order.customerId()).isEqualTo(customer.getId());
        assertThat(order.paymentTerms()).isEqualTo("Net 30");
        assertThat(order.deliveryAddress()).isEqualTo("12 Industrial Estate, Pune");
        assertThat(order.notes()).contains(q.quotationNumber());
        assertThat(order.items()).hasSize(2);
        assertThat(order.items().get(0).productId()).isEqualTo(p1.getId());
        assertThat(order.items().get(0).orderedQty()).isEqualByComparingTo("100");
        assertThat(order.items().get(0).unitPrice()).isEqualByComparingTo("11.25");
        assertThat(order.items().get(1).unitPrice()).isEqualByComparingTo("20");
        assertThat(order.totalValue()).isEqualByComparingTo(q.totalAmount());

        assertThat(auditCount("QUOTATION_CONVERTED", "CRM_QUOTATION", String.valueOf(q.id()))).isEqualTo(1);
        assertThat(auditCount("SO_FROM_QUOTATION", "SALES_ORDER", String.valueOf(order.id()))).isEqualTo(1);
    }

    @Test
    @DisplayName("only an ACCEPTED quotation converts, and never twice")
    void conversionGuards() {
        Customer customer = td.customer();
        Product p = td.product();
        QuotationDto draft = quotation(customer, null, line(p, "1", "10", null));

        assertThatThrownBy(() -> quotationService.convertToSalesOrder(draft.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ACCEPTED");
        quotationService.send(draft.id());
        assertThatThrownBy(() -> quotationService.convertToSalesOrder(draft.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ACCEPTED");

        quotationService.accept(draft.id());
        quotationService.convertToSalesOrder(draft.id());
        assertThatThrownBy(() -> quotationService.convertToSalesOrder(draft.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already converted");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ims.sales_orders WHERE notes LIKE ?", Long.class,
                "%" + draft.quotationNumber() + "%")).isEqualTo(1);
    }

    @Test
    @DisplayName("a rejected quotation cannot be accepted or converted; a draft cannot be accepted")
    void rejectionIsFinal() {
        Customer customer = td.customer();
        QuotationDto q = quotation(customer, null, line(td.product(), "1", "10", null));
        assertThatThrownBy(() -> quotationService.accept(q.id())).isInstanceOf(BusinessRuleException.class);

        quotationService.send(q.id());
        QuotationDto rejected = quotationService.reject(q.id(), "price too high");
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.rejectReason()).isEqualTo("price too high");
        assertThatThrownBy(() -> quotationService.accept(q.id())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> quotationService.convertToSalesOrder(q.id())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("converting a quotation links it to its opportunity and closes the opportunity as won")
    void conversionWinsTheOpportunity() {
        Long owner = user();
        Customer customer = td.customer();
        OpportunityDto opp = opportunityService.create(new SaveOpportunityRequest(null, customer.getId(), null,
                "Compression springs", null, new BigDecimal("5000"), today.plusDays(30), owner, null));
        opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("QUALIFIED", null));
        opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("PROPOSAL", null));

        QuotationDto q = quotation(customer, opp.id(), line(td.product(), "10", "100", null));
        assertThat(q.opportunityId()).isEqualTo(opp.id());
        accepted(q);
        quotationService.convertToSalesOrder(q.id());

        OpportunityDto after = opportunityService.findById(opp.id());
        assertThat(after.convertedQuotationId()).isEqualTo(q.id());
        assertThat(after.stage()).isEqualTo("CLOSED_WON");
        assertThat(after.probabilityPct()).isEqualTo(100);
        assertThat(after.wonAt()).isNotNull();
    }

    @Test
    @DisplayName("an early-stage opportunity is linked but not auto-closed when its quotation converts")
    void earlyStageOpportunityStaysOpen() {
        Long owner = user();
        Customer customer = td.customer();
        OpportunityDto opp = opportunityService.create(new SaveOpportunityRequest(null, customer.getId(), null,
                "Early deal", null, null, null, owner, null));
        QuotationDto q = quotation(customer, opp.id(), line(td.product(), "1", "10", null));
        accepted(q);
        quotationService.convertToSalesOrder(q.id());

        OpportunityDto after = opportunityService.findById(opp.id());
        assertThat(after.convertedQuotationId()).isEqualTo(q.id());
        assertThat(after.stage()).isEqualTo("PROSPECT");
    }

    @Test
    @DisplayName("editing a DRAFT quotation replaces its lines and recomputes the total; other statuses are locked")
    void draftEditing() {
        Customer customer = td.customer();
        Product p1 = td.product();
        Product p2 = td.product();
        QuotationDto q = quotation(customer, null, line(p1, "10", "10", null), line(p2, "5", "20", null));
        assertThat(q.totalAmount()).isEqualByComparingTo("200");

        em.flush();
        Long version = quotationService.findById(q.id()).version();
        QuotationDto edited = quotationService.update(q.id(), new SaveQuotationRequest(null, customer.getId(),
                today.plusDays(10), "Net 45", "revised", List.of(line(p2, "3", "30", "10")), version));
        assertThat(edited.items()).hasSize(1);
        assertThat(edited.items().get(0).lineNo()).isEqualTo(1);
        assertThat(edited.items().get(0).productId()).isEqualTo(p2.getId());
        assertThat(edited.totalAmount()).isEqualByComparingTo("81"); // 3 x 27.00
        assertThat(edited.paymentTerms()).isEqualTo("Net 45");

        quotationService.send(q.id());
        assertThatThrownBy(() -> quotationService.update(q.id(), new SaveQuotationRequest(null, customer.getId(),
                null, null, null, List.of(line(p1, "1", "1", null)), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("DRAFT");
    }

    @Test
    @DisplayName("a stale version is rejected with a version conflict")
    void staleVersion() {
        Customer customer = td.customer();
        Product p = td.product();
        QuotationDto q = quotation(customer, null, line(p, "1", "10", null));
        em.flush();
        Long stale = quotationService.findById(q.id()).version();
        quotationService.update(q.id(), new SaveQuotationRequest(null, customer.getId(), null, null, "first",
                List.of(line(p, "1", "10", null)), stale));
        em.flush();
        assertThatThrownBy(() -> quotationService.update(q.id(), new SaveQuotationRequest(null, customer.getId(),
                null, null, "second", List.of(line(p, "1", "10", null)), stale)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("quotations past their validity date expire, and an expired quotation cannot be converted")
    void expiry() {
        Customer customer = td.customer();
        QuotationDto q = quotation(customer, null, line(td.product(), "1", "10", null));
        quotationService.send(q.id());
        em.flush();
        jdbc.update("UPDATE ims.crm_quotations SET valid_until = ? WHERE id = ?", Date.valueOf(today.minusDays(1)), q.id());
        em.clear();

        assertThatThrownBy(() -> quotationService.accept(q.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("expired");
        assertThat(quotationService.expireOverdue()).isGreaterThanOrEqualTo(1);
        assertThat(quotationService.findById(q.id()).status()).isEqualTo("EXPIRED");
        assertThatThrownBy(() -> quotationService.convertToSalesOrder(q.id())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a quotation needs lines, a customer and a validity date that is not in the past")
    void quotationValidation() {
        Customer customer = td.customer();
        Product p = td.product();
        assertThatThrownBy(() -> quotationService.create(new SaveQuotationRequest(null, null, null, null, null,
                List.of(line(p, "1", "10", null)), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("customer");
        assertThatThrownBy(() -> quotationService.create(new SaveQuotationRequest(null, customer.getId(),
                today.minusDays(1), null, null, List.of(line(p, "1", "10", null)), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("past");
        assertThatThrownBy(() -> quotationService.create(new SaveQuotationRequest(null, 999_999_999L, null, null, null,
                List.of(line(p, "1", "10", null)), null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ── R12-4.1 customer pricing ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("quotation lines default their price from the price list: customer beats standard, volume tiers, validity")
    void priceListDrivesQuotationPrices() {
        Customer gold = td.customer();
        Customer other = td.customer();
        Product p = td.product();
        LocalDate from = today.minusDays(30);
        priceService.create(new SavePriceRequest(null, p.getId(), new BigDecimal("15"), null, from, null, null));
        priceService.create(new SavePriceRequest(gold.getId(), p.getId(), new BigDecimal("12"), null, from, null, null));
        priceService.create(new SavePriceRequest(gold.getId(), p.getId(), new BigDecimal("10"), new BigDecimal("100"),
                from, null, null));
        // expired customer price must be ignored
        priceService.create(new SavePriceRequest(gold.getId(), p.getId(), new BigDecimal("1"), null,
                today.minusDays(60), today.minusDays(40), null));

        assertThat(priceService.resolveDto(gold.getId(), p.getId(), new BigDecimal("10"), today).unitPrice())
                .isEqualByComparingTo("12");
        ResolvedPriceDto bulk = priceService.resolveDto(gold.getId(), p.getId(), new BigDecimal("100"), today);
        assertThat(bulk.unitPrice()).isEqualByComparingTo("10");
        assertThat(bulk.source()).isEqualTo("CUSTOMER");
        ResolvedPriceDto standard = priceService.resolveDto(other.getId(), p.getId(), new BigDecimal("500"), today);
        assertThat(standard.unitPrice()).isEqualByComparingTo("15");
        assertThat(standard.source()).isEqualTo("STANDARD");

        QuotationDto q = quotation(gold, null, line(p, "10", null, null), line(p, "100", null, null));
        assertThat(q.items().get(0).unitPrice()).isEqualByComparingTo("12");
        assertThat(q.items().get(1).unitPrice()).isEqualByComparingTo("10");
        assertThat(q.totalAmount()).isEqualByComparingTo("1120"); // 10 x 12 + 100 x 10
    }

    @Test
    @DisplayName("a line with no price list entry and no explicit price is rejected; an explicit price overrides the list")
    void missingPrice() {
        Customer customer = td.customer();
        Product p = td.product();
        assertThatThrownBy(() -> quotation(customer, null, line(p, "1", null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("No price list entry");

        priceService.create(new SavePriceRequest(null, p.getId(), new BigDecimal("15"), null, today.minusDays(1), null, null));
        QuotationDto q = quotation(customer, null, line(p, "2", "9", null));
        assertThat(q.items().get(0).unitPrice()).isEqualByComparingTo("9");
        assertThat(priceService.resolveDto(customer.getId(), td.product().getId(), BigDecimal.ONE, today).source())
                .isEqualTo("NONE");
    }

    @Test
    @DisplayName("a duplicate price row and an inverted validity range are rejected")
    void priceValidation() {
        Customer customer = td.customer();
        Product p = td.product();
        SavePriceRequest price = new SavePriceRequest(customer.getId(), p.getId(), new BigDecimal("12"), null, today, null, null);
        priceService.create(price);
        assertThatThrownBy(() -> priceService.create(price)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> priceService.create(new SavePriceRequest(customer.getId(), p.getId(), BigDecimal.ONE,
                new BigDecimal("5"), today, today.minusDays(1), null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ── lead -> customer ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a qualified lead converts: customer created, opportunity opened, contact linked, earlier activity moved")
    void leadConvertsToCustomerAndOpportunity() {
        Long owner = user();
        ContactDto contact = contactService.create(new SaveContactRequest(null, "Ravi", "Kumar", "ravi@acme.example",
                "9876543210", "Purchase Manager", "Purchase", null));
        LeadDto lead = leadService.create(new SaveLeadRequest("Acme Springs Pvt Ltd", contact.id(), "WEBSITE", owner,
                new BigDecimal("250000"), today.plusDays(45), "Needs 5000 springs/month", null));
        assertThat(lead.status()).isEqualTo("NEW");
        assertThat(lead.leadNumber()).startsWith("LD/");
        assertThat(lead.allowedActions()).contains("CONTACT", "DISQUALIFY");

        assertThatThrownBy(() -> leadService.qualify(lead.id())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> leadService.convertToOpportunity(lead.id(), null)).isInstanceOf(BusinessRuleException.class);
        leadService.markContacted(lead.id());
        ActivityDto call = activityService.create(new SaveActivityRequest("CALL", null, null, lead.id(), null,
                "Intro call", "Discussed volumes", "Send quote", Instant.now().minus(2, ChronoUnit.HOURS), 20, owner, null));
        assertThat(call.customerId()).isNull();
        assertThat(leadService.qualify(lead.id()).allowedActions()).contains("CONVERT");

        ConvertLeadResultDto result = leadService.convertToOpportunity(lead.id(), new ConvertLeadRequest(null, null));
        assertThat(result.lead().status()).isEqualTo("CONVERTED");
        assertThat(result.lead().convertedCustomerId()).isEqualTo(result.customerId());
        assertThat(result.lead().convertedAt()).isNotNull();
        assertThat(result.customerCode()).startsWith("CUS/");

        Customer customer = customerRepo.findById(result.customerId()).orElseThrow();
        assertThat(customer.getName()).isEqualTo("Acme Springs Pvt Ltd");
        assertThat(customer.getContactPerson()).isEqualTo("Ravi Kumar");
        assertThat(customer.getEmail()).isEqualTo("ravi@acme.example");

        OpportunityDto opp = result.opportunity();
        assertThat(opp.stage()).isEqualTo("PROSPECT");
        assertThat(opp.leadId()).isEqualTo(lead.id());
        assertThat(opp.customerId()).isEqualTo(result.customerId());
        assertThat(opp.estimatedValue()).isEqualByComparingTo("250000");
        assertThat(opp.ownerId()).isEqualTo(owner);
        assertThat(opp.opportunityNumber()).startsWith("OPP/");

        assertThat(contactService.findById(contact.id()).customerId()).isEqualTo(result.customerId());
        List<ActivityDto> log = activityService.search(result.customerId(), null, null, null, null, null, null,
                PageRequest.of(0, 20)).getContent();
        assertThat(log).extracting(ActivityDto::subject).containsExactly("Intro call");

        assertThatThrownBy(() -> leadService.convertToOpportunity(lead.id(), null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> leadService.update(lead.id(), new SaveLeadRequest("Changed", null, null, null, null,
                null, null, null))).isInstanceOf(BusinessRuleException.class);
        assertThat(auditCount("LEAD_CONVERTED", "CRM_LEAD", String.valueOf(lead.id()))).isEqualTo(1);
    }

    @Test
    @DisplayName("a lead can be attached to an existing customer instead of creating one")
    void leadConvertsToExistingCustomer() {
        Long owner = user();
        Customer existing = td.customer();
        long customersBefore = customerRepo.count();
        LeadDto lead = leadService.create(new SaveLeadRequest("Existing Co", null, null, owner, null, null, null, null));
        leadService.markContacted(lead.id());
        leadService.qualify(lead.id());
        ConvertLeadResultDto result = leadService.convertToOpportunity(lead.id(),
                new ConvertLeadRequest(existing.getId(), "Repeat order"));
        assertThat(result.customerId()).isEqualTo(existing.getId());
        assertThat(result.opportunity().description()).isEqualTo("Repeat order");
        assertThat(customerRepo.count()).isEqualTo(customersBefore);
    }

    @Test
    @DisplayName("a disqualified lead needs a reason and is terminal")
    void disqualifiedLeadIsTerminal() {
        Long owner = user();
        LeadDto lead = leadService.create(new SaveLeadRequest("Dead Co", null, null, owner, null, null, null, null));
        LeadDto out = leadService.disqualify(lead.id(), "no budget this year");
        assertThat(out.status()).isEqualTo("UNQUALIFIED");
        assertThat(out.disqualifyReason()).isEqualTo("no budget this year");
        assertThatThrownBy(() -> leadService.markContacted(lead.id())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> opportunityService.create(new SaveOpportunityRequest(lead.id(), null, null, "x", null,
                null, null, owner, null))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("a lead rejects an unknown owner and a stale version")
    void leadReferenceAndVersionChecks() {
        Long owner = user();
        assertThatThrownBy(() -> leadService.create(new SaveLeadRequest("Ghost Co", null, null, 999_999_999L, null, null,
                null, null))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> leadService.create(new SaveLeadRequest("No Owner Co", null, null, null, null, null,
                null, null))).isInstanceOf(NotFoundException.class);

        LeadDto lead = leadService.create(new SaveLeadRequest("Versioned Co", null, null, owner, null, null, null, null));
        leadService.update(lead.id(), new SaveLeadRequest("Versioned Co 2", null, null, null, null, null, null, lead.version()));
        em.flush();
        assertThatThrownBy(() -> leadService.update(lead.id(), new SaveLeadRequest("Versioned Co 3", null, null, null,
                null, null, null, lead.version()))).isInstanceOf(ConflictException.class);
    }

    // ── opportunities ─────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("opportunity stages follow the machine; closing as lost needs a reason; won is terminal")
    void opportunityStages() {
        Long owner = user();
        Customer customer = td.customer();
        OpportunityDto opp = opportunityService.create(new SaveOpportunityRequest(null, customer.getId(), null,
                "Stage walk", null, new BigDecimal("1000"), null, owner, null));
        assertThat(opp.stage()).isEqualTo("PROSPECT");
        assertThat(opp.probabilityPct()).isEqualTo(10);
        assertThat(opp.weightedValue()).isEqualByComparingTo("100");

        assertThatThrownBy(() -> opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("CLOSED_WON", null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("NOPE", null)))
                .isInstanceOf(BusinessRuleException.class);

        OpportunityDto qualified = opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("QUALIFIED", null));
        assertThat(qualified.probabilityPct()).isEqualTo(25);
        opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("PROPOSAL", null));
        OpportunityDto negotiation = opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("NEGOTIATION", null));
        assertThat(negotiation.weightedValue()).isEqualByComparingTo("750");

        assertThatThrownBy(() -> opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("CLOSED_LOST", " ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("lost reason");
        OpportunityDto lost = opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("CLOSED_LOST", "went with a competitor"));
        assertThat(lost.stage()).isEqualTo("CLOSED_LOST");
        assertThat(lost.lostReason()).isEqualTo("went with a competitor");
        assertThat(lost.allowedActions()).isEmpty();
        assertThatThrownBy(() -> opportunityService.advance(opp.id(), new AdvanceOpportunityRequest("PROPOSAL", null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(auditCount("OPPORTUNITY_STAGE", "CRM_OPPORTUNITY", String.valueOf(opp.id()))).isEqualTo(4);
    }

    @Test
    @DisplayName("an opportunity needs a lead or a customer")
    void opportunityNeedsAParty() {
        Long owner = user();
        assertThatThrownBy(() -> opportunityService.create(new SaveOpportunityRequest(null, null, null, "Orphan", null,
                null, null, owner, null))).isInstanceOf(BusinessRuleException.class);
    }

    // ── R12-2.3 / R12-4.4 activity log and contacts ───────────────────────────────────────────────

    @Test
    @DisplayName("the activity log collects everything for a customer, derived from contact, opportunity or direct reference")
    void activityLogPerCustomer() {
        Long owner = user();
        Customer customer = td.customer();
        Customer someoneElse = td.customer();
        ContactDto contact = contactService.create(new SaveContactRequest(customer.getId(), "Meena", "Iyer", null, null,
                null, null, null));
        OpportunityDto opp = opportunityService.create(new SaveOpportunityRequest(null, customer.getId(), contact.id(),
                "Springs RFQ", null, null, null, owner, null));
        Instant base = Instant.now();

        activityService.create(new SaveActivityRequest("CALL", customer.getId(), null, null, null, "Direct call", null,
                null, base.minusSeconds(300), 10, owner, null));
        ActivityDto viaContact = activityService.create(new SaveActivityRequest("MEETING", null, contact.id(), null, null,
                "Site visit", "Toured the plant", "Positive", base.minusSeconds(200), 90, owner, null));
        ActivityDto viaOpp = activityService.create(new SaveActivityRequest("EMAIL", null, null, null, opp.id(),
                "Sent drawings", null, null, base.minusSeconds(100), null, owner, null));
        activityService.create(new SaveActivityRequest("FOLLOW_UP", someoneElse.getId(), null, null, null, "Other customer",
                null, null, base, null, owner, null));

        assertThat(viaContact.customerId()).isEqualTo(customer.getId());
        assertThat(viaContact.contactName()).isEqualTo("Meena Iyer");
        assertThat(viaOpp.customerId()).isEqualTo(customer.getId());

        List<ActivityDto> log = activityService.search(customer.getId(), null, null, null, null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "activityDate"))).getContent();
        assertThat(log).extracting(ActivityDto::subject).containsExactly("Sent drawings", "Site visit", "Direct call");

        assertThat(activityService.search(customer.getId(), null, null, null, "MEETING", null, null, PageRequest.of(0, 20))
                .getContent()).extracting(ActivityDto::subject).containsExactly("Site visit");
        assertThat(activityService.search(null, contact.id(), null, null, null, null, null, PageRequest.of(0, 20))
                .getContent()).extracting(ActivityDto::subject).containsExactly("Site visit");
        assertThat(activityService.search(customer.getId(), null, null, null, null, base.minusSeconds(250),
                base.minusSeconds(150), PageRequest.of(0, 20)).getContent())
                .extracting(ActivityDto::subject).containsExactly("Site visit");
    }

    @Test
    @DisplayName("an activity must reference something, use a known type, and can be edited and removed")
    void activityValidationAndLifecycle() {
        Long owner = user();
        Customer customer = td.customer();
        assertThatThrownBy(() -> activityService.create(new SaveActivityRequest("CALL", null, null, null, null, "Floating",
                null, null, null, null, owner, null))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> activityService.create(new SaveActivityRequest("TELEPATHY", customer.getId(), null, null,
                null, "Bad type", null, null, null, null, owner, null))).isInstanceOf(BusinessRuleException.class);

        ActivityDto created = activityService.create(new SaveActivityRequest("CALL", customer.getId(), null, null, null,
                "Check in", null, null, null, 5, owner, null));
        assertThat(created.activityDate()).isNotNull();
        ActivityDto updated = activityService.update(created.id(), new SaveActivityRequest("CALL", customer.getId(), null,
                null, null, "Check in (done)", "all good", "Order expected", null, 7, owner, created.version()));
        assertThat(updated.subject()).isEqualTo("Check in (done)");
        assertThat(updated.outcome()).isEqualTo("Order expected");
        activityService.delete(created.id());
        assertThatThrownBy(() -> activityService.findById(created.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("contacts link to the customer master, can be searched and are deactivated rather than deleted")
    void contacts() {
        Customer customer = td.customer();
        ContactDto c = contactService.create(new SaveContactRequest(customer.getId(), "Anil", "Shah", "anil@x.example",
                null, "Buyer", null, null));
        assertThat(c.customerName()).isEqualTo(customer.getName());
        assertThat(contactService.search(customer.getId(), "shah", true, PageRequest.of(0, 10)).getContent())
                .extracting(ContactDto::id).containsExactly(c.id());
        contactService.deactivate(c.id());
        assertThat(contactService.search(customer.getId(), null, true, PageRequest.of(0, 10)).getContent()).isEmpty();
        assertThat(contactService.findById(c.id()).active()).isFalse();
        assertThatThrownBy(() -> contactService.create(new SaveContactRequest(999_999_999L, "Ghost", null, null, null,
                null, null, null))).isInstanceOf(NotFoundException.class);
    }

    // ── R12-4.2 segmentation ──────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("customers can be tagged, filtered by tag, re-tagged by difference, and tags in use cannot be deleted")
    void segmentation() {
        Customer tagged = td.customer();
        Customer untagged = td.customer();
        TagDto auto = tagService.createTag(new SaveTagRequest("INDUSTRY", "Auto-" + td.code("t"), null));
        TagDto gold = tagService.createTag(new SaveTagRequest("TIER", "Gold-" + td.code("t"), null));
        assertThatThrownBy(() -> tagService.createTag(new SaveTagRequest("INDUSTRY", auto.name(), null)))
                .isInstanceOf(ConflictException.class);

        CrmCustomerDto result = tagService.setCustomerTags(tagged.getId(), List.of(auto.id(), gold.id()));
        assertThat(result.tags()).extracting(TagDto::id).containsExactlyInAnyOrder(auto.id(), gold.id());

        List<Long> withAuto = tagService.searchCustomers(null, auto.id(), PageRequest.of(0, 50)).getContent().stream()
                .map(CrmCustomerDto::id).toList();
        assertThat(withAuto).containsExactly(tagged.getId());
        assertThat(tagService.searchCustomers(null, null, PageRequest.of(0, 5000)).getContent())
                .extracting(CrmCustomerDto::id).contains(tagged.getId(), untagged.getId());
        assertThat(tagService.searchCustomers(tagged.getCustomerCode(), gold.id(), PageRequest.of(0, 5)).getContent())
                .hasSize(1);

        assertThatThrownBy(() -> tagService.deleteTag(auto.id())).isInstanceOf(BusinessRuleException.class);
        CrmCustomerDto narrowed = tagService.setCustomerTags(tagged.getId(), List.of(gold.id()));
        assertThat(narrowed.tags()).extracting(TagDto::id).containsExactly(gold.id());
        tagService.deleteTag(auto.id());
        assertThatThrownBy(() -> tagService.setCustomerTags(tagged.getId(), List.of(auto.id())))
                .isInstanceOf(NotFoundException.class);
        assertThat(tagService.setCustomerTags(tagged.getId(), List.of()).tags()).isEmpty();
    }

    // ── R12-4.3 pipeline report ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the pipeline report groups opportunities by stage with count, value, weighted value and close dates")
    void pipelineReport() {
        Long owner = user();
        Customer customer = td.customer();
        OpportunityDto early = opportunityService.create(new SaveOpportunityRequest(null, customer.getId(), null, "Early",
                null, new BigDecimal("1000"), today.plusDays(10), owner, null));
        OpportunityDto early2 = opportunityService.create(new SaveOpportunityRequest(null, customer.getId(), null, "Early 2",
                null, new BigDecimal("3000"), today.plusDays(40), owner, null));
        OpportunityDto late = opportunityService.create(new SaveOpportunityRequest(null, customer.getId(), null, "Late",
                null, new BigDecimal("2000"), today.plusDays(20), owner, null));
        opportunityService.advance(late.id(), new AdvanceOpportunityRequest("QUALIFIED", null));
        opportunityService.advance(late.id(), new AdvanceOpportunityRequest("PROPOSAL", null));
        assertThat(early.id()).isNotEqualTo(early2.id());
        em.flush();

        List<Map<String, Object>> rows = pipelineReport.runQuery(new ReportRequest(null, null, owner, null, 0, 100));
        assertThat(rows).extracting(r -> r.get("Stage")).containsExactly("PROSPECT", "PROPOSAL");
        Map<String, Object> prospect = rows.get(0);
        assertThat(((Number) prospect.get("Opportunities")).longValue()).isEqualTo(2);
        assertThat(new BigDecimal(prospect.get("Total Value").toString())).isEqualByComparingTo("4000");
        assertThat(new BigDecimal(prospect.get("Weighted Value").toString())).isEqualByComparingTo("400"); // 10%
        Map<String, Object> proposal = rows.get(1);
        assertThat(new BigDecimal(proposal.get("Weighted Value").toString())).isEqualByComparingTo("1000"); // 50%

        List<Map<String, Object>> filtered = pipelineReport.runQuery(
                new ReportRequest(today.plusDays(15), today.plusDays(30), owner, null, 0, 100));
        assertThat(filtered).extracting(r -> r.get("Stage")).containsExactly("PROPOSAL");
        assertThat(pipelineReport.runQuery(new ReportRequest(null, null, owner, "PROPOSAL", 0, 100))).hasSize(1);
    }
}
