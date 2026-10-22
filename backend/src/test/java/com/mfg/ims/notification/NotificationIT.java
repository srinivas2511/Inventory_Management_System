package com.mfg.ims.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.event.PurchaseOrderApprovedEvent;
import com.mfg.ims.iam.dto.CreateUserRequest;
import com.mfg.ims.iam.service.UserAdminService;
import com.mfg.ims.insight.alert.AlertService;
import com.mfg.ims.notification.service.EmailSender;
import com.mfg.ims.notification.service.NotificationOutboxJob;
import com.mfg.ims.notification.service.NotificationService;
import com.mfg.ims.notification.service.NotificationService.Request;
import com.mfg.ims.procurement.dto.CreatePoRequest;
import com.mfg.ims.procurement.service.PurchaseOrderService;
import com.mfg.ims.support.AbstractIntegrationTest;
import com.mfg.ims.support.TestData;

/**
 * The notification engine end to end against real PostgreSQL: events become in-app and e-mail notifications after the
 * originating transaction commits, preferences and templates are honoured, the outbox retries and gives up, and
 * nothing is sent twice. E-mail goes to a recording sender, so no SMTP server is involved.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(NotificationIT.MailConfig.class)
class NotificationIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Notif#Test-2026";
    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "QUALITY_MANAGER", "PURCHASE_MANAGER",
            "OPERATOR");

    record Mail(String to, String subject, String body) {
    }

    static class RecordingEmailSender implements EmailSender {
        final List<Mail> sent = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public void send(String to, String subject, String body) {
            if (failing) {
                throw new IllegalStateException("SMTP server unavailable");
            }
            sent.add(new Mail(to, subject, body));
        }
    }

    @TestConfiguration
    static class MailConfig {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;
    @Autowired AlertService alertService;
    @Autowired NotificationService notificationService;
    @Autowired NotificationOutboxJob outbox;
    @Autowired PurchaseOrderService poService;
    @Autowired RecordingEmailSender mail;
    @Autowired ApplicationEventPublisher events;
    @Autowired CacheManager cacheManager;
    @Autowired PlatformTransactionManager txManager;

    private final java.util.Map<String, String> tokens = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, Long> userIds = new java.util.LinkedHashMap<>();
    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void createUsersAndLogIn() throws Exception {
        var actor = new CachedUser(9003L, "notif-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            for (String role : ROLES) {
                userAdminService.create(new CreateUserRequest(username(role), null, "NTF " + role,
                        username(role) + "@ntf.test", null, PASSWORD, Set.of(role)));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        // admin-created users start with a temporary password; these tests are about the endpoints, not that step
        jdbc.update("UPDATE users SET must_change_password = FALSE WHERE email LIKE ?", "%@ntf.test");
        for (String role : ROLES) {
            MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username(role) + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andReturn();
            assertThat(login.getResponse().getStatus()).as("login as %s", role).isEqualTo(200);
            tokens.put(role, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
            userIds.put(role, jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class,
                    username(role)));
        }
    }

    @AfterAll
    void restoreSettingsAndRemoveTheUsers() {
        setSetting("notification.email.max_attempts", "5");
        setSetting("notification.alert.email_min_severity", "HIGH");
        jdbc.update("UPDATE notification_templates SET active = TRUE");
        String like = "ntf_%_" + runId;
        jdbc.update("DELETE FROM notifications WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM notification_subscriptions WHERE user_id IN "
                + "(SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
    }

    @BeforeEach
    void resetTheMailSender() {
        mail.failing = false;
        mail.sent.clear();
    }

    private String username(String role) {
        return "ntf_" + role.toLowerCase() + "_" + runId;
    }

    private Long uid(String role) {
        return userIds.get(role);
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private MvcResult call(String role, MockHttpServletRequestBuilder b) throws Exception {
        return mockMvc.perform(b.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(role))).andReturn();
    }

    private MvcResult sendJson(String role, MockHttpServletRequestBuilder b, String body) throws Exception {
        return call(role, b.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private static <T> T json(MvcResult r, String path) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), path);
    }

    private long inAppCount(String role, String template) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND template_code = ? "
                + "AND channel = 'IN_APP'", Long.class, uid(role), template);
    }

    private long emailCount(String role, String template) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND template_code = ? "
                + "AND channel = 'EMAIL'", Long.class, uid(role), template);
    }

    private long raise(String type, String severity, String message) {
        long entityId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        return alertService.raise(type, severity, "TEST", entityId, message).getId();
    }

    private void setSetting(String key, String value) {
        jdbc.update("UPDATE system_settings SET value = ? WHERE key = ?", value, key);
        cacheManager.getCache("systemSettings").clear();
    }

    private void actAs(String role) {
        var principal = new CachedUser(uid(role), username(role), true, 1, Set.of(), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    // ---- alerts ---------------------------------------------------------------------------------------------------

    @Test
    void aNewAlertAppearsInAppForEveryAlertViewerAndForNoOneElse() {
        long before = inAppCount("ADMIN", "LOW_STOCK");
        long qualityBefore = inAppCount("QUALITY_MANAGER", "LOW_STOCK");
        long operatorBefore = inAppCount("OPERATOR", "LOW_STOCK");

        long alertId = raise("LOW_STOCK", "MEDIUM", "Spring wire 2.0 mm is below its reorder level");

        assertThat(inAppCount("ADMIN", "LOW_STOCK")).isEqualTo(before + 1);
        assertThat(inAppCount("QUALITY_MANAGER", "LOW_STOCK")).isEqualTo(qualityBefore + 1);
        assertThat(inAppCount("OPERATOR", "LOW_STOCK")).as("OPERATOR holds no ALERT_VIEW").isEqualTo(operatorBefore);
        assertThat(inAppCount("ENGINEER", "LOW_STOCK")).as("ENGINEER holds no ALERT_VIEW").isZero();

        Map<String, Object> row = jdbc.queryForMap("SELECT subject, body, status, entity_type, entity_id, sent_at "
                + "FROM notifications WHERE user_id = ? AND dedup_key = ?", uid("ADMIN"),
                "ALERT:" + alertId + ":IN_APP:" + uid("ADMIN"));
        assertThat(row.get("subject")).isEqualTo("[MEDIUM] Low stock");
        assertThat(row.get("body")).isEqualTo("Spring wire 2.0 mm is below its reorder level");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("entity_type")).isEqualTo("ALERT");
        assertThat(((Number) row.get("entity_id")).longValue()).isEqualTo(alertId);
        assertThat(row.get("sent_at")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE channel = 'EMAIL' "
                + "AND dedup_key LIKE ?", Long.class, "ALERT:" + alertId + ":EMAIL:%"))
                .as("a MEDIUM alert is not e-mailed").isZero();
    }

    @Test
    void repeatSightingsOfAnOpenAlertDoNotNotifyAgain() {
        long entityId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
        alertService.raise("OVERDUE_PO", "MEDIUM", "TEST", entityId, "PO is overdue");
        long once = inAppCount("ADMIN", "OVERDUE_PO");
        alertService.raise("OVERDUE_PO", "MEDIUM", "TEST", entityId, "PO is still overdue");
        alertService.raise("OVERDUE_PO", "MEDIUM", "TEST", entityId, "PO is overdue, day 3");
        assertThat(inAppCount("ADMIN", "OVERDUE_PO")).isEqualTo(once);
    }

    @Test
    void onlyAlertsAtOrAboveTheConfiguredSeverityAreAlsoEmailed() {
        mail.sent.clear();
        long emailsBefore = emailCount("ADMIN", "PRODUCTION_DELAY");
        raise("PRODUCTION_DELAY", "MEDIUM", "Order PO-1 is two days late");
        assertThat(emailCount("ADMIN", "PRODUCTION_DELAY")).isEqualTo(emailsBefore);

        long alertId = raise("PRODUCTION_DELAY", "HIGH", "Order PO-2 is a week late");
        assertThat(emailCount("ADMIN", "PRODUCTION_DELAY")).isEqualTo(emailsBefore + 1);

        outbox.processDue();
        assertThat(mail.sent).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo(username("ADMIN") + "@ntf.test");
            assertThat(m.subject()).isEqualTo("[HIGH] Production delay");
            assertThat(m.body()).isEqualTo("Order PO-2 is a week late");
        });
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE dedup_key = ?", String.class,
                "ALERT:" + alertId + ":EMAIL:" + uid("ADMIN"))).isEqualTo("SENT");

        setSetting("notification.alert.email_min_severity", "MEDIUM");
        try {
            long mediumEmails = emailCount("ADMIN", "PRODUCTION_DELAY");
            raise("PRODUCTION_DELAY", "MEDIUM", "Order PO-3 is late");
            assertThat(emailCount("ADMIN", "PRODUCTION_DELAY")).isEqualTo(mediumEmails + 1);
        } finally {
            setSetting("notification.alert.email_min_severity", "HIGH");
        }
    }

    @Test
    void everyAlertTypeTheDatabaseAllowsHasATemplateOrFallsBackToTheGenericOne() {
        List<String> types = jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'chk_alert_type'", String.class).stream()
                .flatMap(def -> java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(def).results())
                .map(m -> m.group(1)).toList();
        assertThat(types).contains("LOW_STOCK", "SPC_OUT_OF_CONTROL");
        List<String> templates = jdbc.queryForList("SELECT template_code FROM notification_templates "
                + "WHERE active = TRUE", String.class);
        assertThat(templates).contains("ALERT_GENERIC");
        assertThat(templates).containsAll(types);
    }

    // ---- inbox ----------------------------------------------------------------------------------------------------

    @Test
    void theInboxListsOnlyYourOwnNotificationsAndReadingThemLowersTheCount() throws Exception {
        raise("AGING_BATCH", "LOW", "Batch B-1 is ageing");
        long engineerUnread = ((Number) json(call("ENGINEER", get("/api/notifications/unread-count")), "$.unread"))
                .longValue();
        long unread = ((Number) json(call("ADMIN", get("/api/notifications/unread-count")), "$.unread")).longValue();
        assertThat(unread).isPositive();

        MvcResult inbox = call("ADMIN", get("/api/notifications").param("unreadOnly", "true"));
        assertThat(status(inbox)).isEqualTo(200);
        List<Integer> ids = json(inbox, "$.content[*].id");
        List<String> subjects = json(inbox, "$.content[*].subject");
        assertThat(subjects).contains("[LOW] Ageing batch");
        List<Integer> agingIds = JsonPath.read(inbox.getResponse().getContentAsString(),
                "$.content[?(@.templateCode=='AGING_BATCH')].id");
        long id = agingIds.get(0);
        assertThat(ids).contains((int) id);

        assertThat(status(call("ENGINEER", post("/api/notifications/{id}/read", id)))).as("someone else's")
                .isEqualTo(404);
        MvcResult read = call("ADMIN", post("/api/notifications/{id}/read", id));
        assertThat(status(read)).isEqualTo(200);
        assertThat((Boolean) json(read, "$.read")).isTrue();
        assertThat(((Number) json(call("ADMIN", get("/api/notifications/unread-count")), "$.unread")).longValue())
                .isEqualTo(unread - 1);
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE id = ?", String.class, id))
                .isEqualTo("READ");

        MvcResult unreadOnly = call("ADMIN", get("/api/notifications").param("unreadOnly", "true"));
        assertThat(JsonPath.<List<Integer>>read(unreadOnly.getResponse().getContentAsString(), "$.content[*].id"))
                .doesNotContain((int) id);

        MvcResult all = call("ADMIN", post("/api/notifications/read-all"));
        assertThat(status(all)).isEqualTo(200);
        assertThat(((Number) json(call("ADMIN", get("/api/notifications/unread-count")), "$.unread")).longValue())
                .isZero();
        assertThat(((Number) json(call("ENGINEER", get("/api/notifications/unread-count")), "$.unread")).longValue())
                .as("another user's count is untouched").isEqualTo(engineerUnread);
    }

    @Test
    void emailRowsNeverAppearInTheInbox() throws Exception {
        raise("PENDING_PO", "CRITICAL", "PO is stuck");
        MvcResult inbox = call("ADMIN", get("/api/notifications").param("size", "200"));
        List<String> templates = json(inbox, "$.content[*].templateCode");
        assertThat(templates).contains("PENDING_PO");
        assertThat(java.util.Collections.frequency(templates, "PENDING_PO"))
                .as("only the in-app rows, never the e-mail rows").isEqualTo((int) inAppCount("ADMIN", "PENDING_PO"));
        assertThat(emailCount("ADMIN", "PENDING_PO")).isPositive();
    }

    // ---- preferences ----------------------------------------------------------------------------------------------

    @Test
    void unsubscribingFromAChannelStopsThatChannelForThatUserOnly() throws Exception {
        MvcResult prefs = call("QUALITY_MANAGER", get("/api/notification-preferences"));
        assertThat(status(prefs)).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(prefs.getResponse().getContentAsString(), "$[*].templateCode"))
                .contains("LOW_STOCK", "PO_APPROVED", "CAPA_ASSIGNED");
        assertThat(JsonPath.<List<Boolean>>read(prefs.getResponse().getContentAsString(),
                "$[?(@.templateCode=='LOW_STOCK')].channels[*].enabled")).containsOnly(true);
        assertThat(JsonPath.<List<String>>read(prefs.getResponse().getContentAsString(),
                "$[?(@.templateCode=='CAPA_ASSIGNED')].channels[*].channel")).containsExactly("IN_APP");

        MvcResult off = sendJson("QUALITY_MANAGER", put("/api/notification-preferences"),
                "{\"templateCode\":\"MATERIAL_SHORTAGE\",\"channel\":\"IN_APP\",\"enabled\":false}");
        assertThat(status(off)).isEqualTo(200);
        assertThat(JsonPath.<List<Boolean>>read(off.getResponse().getContentAsString(),
                "$[?(@.templateCode=='MATERIAL_SHORTAGE')].channels[?(@.channel=='IN_APP')].enabled"))
                .containsExactly(false);

        long qualityBefore = inAppCount("QUALITY_MANAGER", "MATERIAL_SHORTAGE");
        long adminBefore = inAppCount("ADMIN", "MATERIAL_SHORTAGE");
        raise("MATERIAL_SHORTAGE", "LOW", "Shortage of wire");
        assertThat(inAppCount("QUALITY_MANAGER", "MATERIAL_SHORTAGE")).as("opted out").isEqualTo(qualityBefore);
        assertThat(inAppCount("ADMIN", "MATERIAL_SHORTAGE")).as("others unaffected").isEqualTo(adminBefore + 1);

        sendJson("QUALITY_MANAGER", put("/api/notification-preferences"),
                "{\"templateCode\":\"MATERIAL_SHORTAGE\",\"channel\":\"IN_APP\",\"enabled\":true}");
        raise("MATERIAL_SHORTAGE", "LOW", "Shortage of wire again");
        assertThat(inAppCount("QUALITY_MANAGER", "MATERIAL_SHORTAGE")).isEqualTo(qualityBefore + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_subscriptions WHERE user_id = ? "
                + "AND template_code = 'MATERIAL_SHORTAGE'", Long.class, uid("QUALITY_MANAGER")))
                .as("one row per channel, updated in place").isEqualTo(1);
    }

    @Test
    void preferencesRejectUnknownTemplatesAndChannelsTheTemplateDoesNotUse() throws Exception {
        assertThat(status(sendJson("ENGINEER", put("/api/notification-preferences"),
                "{\"templateCode\":\"NO_SUCH\",\"channel\":\"IN_APP\",\"enabled\":false}"))).isEqualTo(404);
        assertThat(status(sendJson("ENGINEER", put("/api/notification-preferences"),
                "{\"templateCode\":\"CAPA_ASSIGNED\",\"channel\":\"EMAIL\",\"enabled\":false}"))).isEqualTo(400);
        assertThat(status(sendJson("ENGINEER", put("/api/notification-preferences"),
                "{\"templateCode\":\"LOW_STOCK\",\"channel\":\"SMS\",\"enabled\":false}"))).isEqualTo(400);
    }

    // ---- purchase orders ------------------------------------------------------------------------------------------

    private long approvedPo(Long supplierId, String requesterRole) {
        var line = new CreatePoRequest.PoLineRequest(td.material().getId(), new BigDecimal("100.000"),
                new BigDecimal("50.0000"), new BigDecimal("18.00"));
        actAs(requesterRole);
        try {
            long poId = poService.create(new CreatePoRequest(supplierId, LocalDate.now(), LocalDate.now().plusDays(7),
                    "NET30", null, null, List.of(line))).id();
            poService.submit(poId);
            actAs("ADMIN");
            poService.approve(poId);
            return poId;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void approvingAPurchaseOrderEmailsTheSupplierContactAndNotifiesWhoSubmittedIt() {
        Long supplierId = td.supplier().getId();
        jdbc.update("UPDATE suppliers SET email = ?, contact_person = ? WHERE id = ?", "buyer@acme.test", "Mr Buyer",
                supplierId);

        long poId = approvedPo(supplierId, "PURCHASE_MANAGER");
        String poNumber = jdbc.queryForObject("SELECT po_number FROM purchase_orders WHERE id = ?", String.class, poId);

        Map<String, Object> supplierMail = jdbc.queryForMap("SELECT user_id, recipient_email, subject, body, status, "
                + "entity_type, entity_id FROM notifications WHERE template_code = 'PO_APPROVED_SUPPLIER' "
                + "AND entity_id = ?", poId);
        assertThat(supplierMail.get("user_id")).as("an external recipient has no user").isNull();
        assertThat(supplierMail.get("recipient_email")).isEqualTo("buyer@acme.test");
        assertThat(supplierMail.get("subject")).isEqualTo("Purchase order " + poNumber + " approved");
        assertThat(supplierMail.get("body").toString()).startsWith("Dear Mr Buyer,")
                .contains(poNumber).contains("Manufacturing Co.");
        assertThat(supplierMail.get("status")).isEqualTo("PENDING");
        assertThat(supplierMail.get("entity_type")).isEqualTo("PURCHASE_ORDER");

        Map<String, Object> inApp = jdbc.queryForMap("SELECT subject, body, status FROM notifications "
                + "WHERE template_code = 'PO_APPROVED' AND user_id = ? AND entity_id = ?",
                uid("PURCHASE_MANAGER"), poId);
        assertThat(inApp.get("subject")).isEqualTo("Purchase order " + poNumber + " approved");
        assertThat(inApp.get("body").toString()).contains("IT Supplier");
        assertThat(inApp.get("status")).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE template_code = 'PO_APPROVED' "
                + "AND entity_id = ? AND user_id = ?", Long.class, poId, uid("ADMIN")))
                .as("the approver is not told about their own approval").isZero();

        outbox.processDue();
        assertThat(mail.sent).anySatisfy(m -> {
            assertThat(m.to()).isEqualTo("buyer@acme.test");
            assertThat(m.subject()).isEqualTo("Purchase order " + poNumber + " approved");
        });
        assertThat(jdbc.queryForObject("SELECT status FROM notifications WHERE template_code = "
                + "'PO_APPROVED_SUPPLIER' AND entity_id = ?", String.class, poId)).isEqualTo("SENT");

        // the same event delivered again (a replay) must not notify twice
        events.publishEvent(new PurchaseOrderApprovedEvent(poId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE entity_id = ? "
                + "AND entity_type = 'PURCHASE_ORDER'", Long.class, poId)).isEqualTo(2L);
    }

    @Test
    void aSupplierWithoutAnEmailAddressIsSimplySkipped() {
        Long supplierId = td.supplier().getId();
        jdbc.update("UPDATE suppliers SET email = NULL WHERE id = ?", supplierId);

        long poId = approvedPo(supplierId, "PURCHASE_MANAGER");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE template_code = "
                + "'PO_APPROVED_SUPPLIER' AND entity_id = ?", Long.class, poId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE template_code = 'PO_APPROVED' "
                + "AND entity_id = ?", Long.class, poId)).isEqualTo(1L);
    }

    @Test
    void aNotificationFailureCannotRollBackTheTransactionThatAskedForIt() {
        String marker = "marker-" + runId;
        new TransactionTemplate(txManager).executeWithoutResult(status -> {
            jdbc.update("UPDATE notification_templates SET description = ? WHERE template_code = 'ALERT_GENERIC'",
                    marker);
            // a recipient address longer than the column makes the insert fail inside the notification transaction
            assertThatThrownBy(() -> notificationService.send(Request.toEmail("PO_APPROVED_SUPPLIER",
                    "x".repeat(300) + "@acme.test", Map.of("poNumber", "PO/1"), null, null, null)))
                    .isInstanceOf(RuntimeException.class);
        });
        try {
            assertThat(jdbc.queryForObject("SELECT description FROM notification_templates "
                    + "WHERE template_code = 'ALERT_GENERIC'", String.class))
                    .as("the caller's own work was committed despite the failed notification").isEqualTo(marker);
        } finally {
            jdbc.update("UPDATE notification_templates SET description = 'Alert of a type without its own template' "
                    + "WHERE template_code = 'ALERT_GENERIC'");
        }
    }

    // ---- outbox ---------------------------------------------------------------------------------------------------

    private long queueEmail(String address, String dedupKey) {
        notificationService.send(Request.toEmail("PO_APPROVED_SUPPLIER", address, Map.of("poNumber", "PO/OUTBOX",
                "contactName", "Sir", "companyName", "Acme", "orderDate", "2026-10-04"), "PURCHASE_ORDER", 1L,
                dedupKey));
        return jdbc.queryForObject("SELECT id FROM notifications WHERE recipient_email = ? ORDER BY id DESC LIMIT 1",
                Long.class, address);
    }

    private void makeDue(long id) {
        // the JVM clock, as the outbox uses; the database container's own clock can drift from the host's
        jdbc.update("UPDATE notifications SET next_attempt_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5)), id);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("SELECT status, attempts, next_attempt_at, last_error, sent_at FROM notifications "
                + "WHERE id = ?", id);
    }

    @Test
    void aFailedEmailIsRetriedWithBackOffAndGivesUpAfterTheConfiguredAttempts() throws Exception {
        setSetting("notification.email.max_attempts", "3");
        mail.failing = true;
        String address = "retry-" + runId + "@acme.test";
        long id = queueEmail(address, null);

        outbox.processDue();
        Map<String, Object> first = row(id);
        assertThat(first.get("status")).isEqualTo("PENDING");
        assertThat(first.get("attempts")).isEqualTo(1);
        assertThat(first.get("last_error")).isEqualTo("IllegalStateException: SMTP server unavailable");
        assertThat(((java.sql.Timestamp) first.get("next_attempt_at")).toInstant())
                .isAfter(java.time.Instant.now().plusSeconds(30));

        outbox.processDue();
        assertThat(row(id).get("attempts")).as("not due yet, so not tried again").isEqualTo(1);

        makeDue(id);
        outbox.processDue();
        assertThat(row(id).get("attempts")).isEqualTo(2);
        assertThat(row(id).get("status")).isEqualTo("PENDING");

        makeDue(id);
        outbox.processDue();
        Map<String, Object> last = row(id);
        assertThat(last.get("status")).isEqualTo("FAILED");
        assertThat(last.get("attempts")).isEqualTo(3);
        assertThat(last.get("next_attempt_at")).isNull();
        assertThat(last.get("sent_at")).isNull();

        mail.failing = false;
        outbox.processDue();
        assertThat(row(id).get("status")).as("a FAILED message is not retried by itself").isEqualTo("FAILED");
        assertThat(mail.sent).noneMatch(m -> m.to().equals(address));

        MvcResult log = call("ADMIN", get("/api/notification-log").param("status", "FAILED").param("channel", "EMAIL")
                .param("size", "200"));
        assertThat(status(log)).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(log.getResponse().getContentAsString(), "$.content[*].recipientEmail"))
                .contains(address);
        assertThat(JsonPath.<List<String>>read(log.getResponse().getContentAsString(),
                "$.content[?(@.recipientEmail=='" + address + "')].lastError"))
                .containsExactly("IllegalStateException: SMTP server unavailable");
    }

    @Test
    void anEmailThatSucceedsOnALaterAttemptIsSentExactlyOnce() {
        mail.failing = true;
        String address = "later-" + runId + "@acme.test";
        long id = queueEmail(address, null);
        outbox.processDue();
        assertThat(row(id).get("attempts")).isEqualTo(1);

        mail.failing = false;
        makeDue(id);
        outbox.processDue();
        outbox.processDue();

        Map<String, Object> done = row(id);
        assertThat(done.get("status")).isEqualTo("SENT");
        assertThat(done.get("attempts")).isEqualTo(2);
        assertThat(done.get("last_error")).isNull();
        assertThat(done.get("sent_at")).isNotNull();
        assertThat(mail.sent).filteredOn(m -> m.to().equals(address)).hasSize(1);
    }

    @Test
    void theSameDedupKeyNeverQueuesTheSameEmailTwice() {
        String address = "dedup-" + runId + "@acme.test";
        queueEmail(address, "ONCE-" + runId);
        queueEmail(address, "ONCE-" + runId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_email = ?", Long.class,
                address)).isEqualTo(1L);
    }

    // ---- CAPA -----------------------------------------------------------------------------------------------------

    @Test
    void assigningACapaNotifiesTheNewAssigneeOnlyWhenTheAssigneeChanges() throws Exception {
        long engineer = uid("ENGINEER");
        long operatorId = uid("OPERATOR");
        MvcResult created = sendJson("QUALITY_MANAGER", post("/api/capa"),
                "{\"title\":\"Burr on coil ends\",\"sourceType\":\"INTERNAL_AUDIT\",\"assignedTo\":" + engineer + "}");
        assertThat(status(created)).as(created.getResponse().getContentAsString()).isEqualTo(201);
        long capaId = ((Number) json(created, "$.id")).longValue();
        String capaNumber = json(created, "$.capaNumber");

        Map<String, Object> note = jdbc.queryForMap("SELECT subject, body, status FROM notifications "
                + "WHERE template_code = 'CAPA_ASSIGNED' AND user_id = ? AND entity_id = ?", engineer, capaId);
        assertThat(note.get("subject")).isEqualTo("CAPA " + capaNumber + " assigned to you");
        assertThat(note.get("body").toString()).contains("Burr on coil ends");
        assertThat(note.get("status")).isEqualTo("SENT");

        String update = "{\"title\":\"Burr on coil ends\",\"sourceType\":\"INTERNAL_AUDIT\",\"assignedTo\":%d}";
        sendJson("QUALITY_MANAGER", put("/api/capa/{id}", capaId), update.formatted(engineer));
        assertThat(inAppCountFor(engineer, capaId)).as("same assignee, no second notice").isEqualTo(1L);

        sendJson("QUALITY_MANAGER", put("/api/capa/{id}", capaId), update.formatted(operatorId));
        assertThat(inAppCountFor(operatorId, capaId)).as("new assignee is told").isEqualTo(1L);

        sendJson("QUALITY_MANAGER", put("/api/capa/{id}", capaId), update.formatted(uid("QUALITY_MANAGER")));
        assertThat(inAppCountFor(uid("QUALITY_MANAGER"), capaId)).as("self-assignment needs no notice").isZero();
    }

    private long inAppCountFor(long userId, long capaId) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE template_code = 'CAPA_ASSIGNED' "
                + "AND user_id = ? AND entity_id = ?", Long.class, userId, capaId);
    }

    // ---- templates ------------------------------------------------------------------------------------------------

    @Test
    void administratorsMaintainTemplatesAndTheChangeAppliesToTheNextNotification() throws Exception {
        MvcResult templates = call("ADMIN", get("/api/notification-templates"));
        assertThat(status(templates)).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(templates.getResponse().getContentAsString(), "$[*].templateCode"))
                .contains("OVERSTOCK", "PO_APPROVED_SUPPLIER", "ALERT_GENERIC");
        List<Integer> overstockIds = JsonPath.read(templates.getResponse().getContentAsString(),
                "$[?(@.templateCode=='OVERSTOCK')].id");
        long id = overstockIds.get(0);

        String edit = "{\"channel\":\"IN_APP\",\"description\":\"Overstock\",\"subjectTemplate\":\"Too much: {{entityId}}\","
                + "\"bodyTemplate\":\"Custom text. {{message}}\",\"active\":%s}";
        try {
            MvcResult changed = sendJson("ADMIN", put("/api/notification-templates/{id}", id), edit.formatted("true"));
            assertThat(status(changed)).isEqualTo(200);
            long entityId = 4_000_000_001L + ThreadLocalRandom.current().nextLong(1000);
            alertService.raise("OVERSTOCK", "LOW", "TEST", entityId, "Bin A is over its maximum");
            assertThat(jdbc.queryForObject("SELECT body FROM notifications WHERE user_id = ? AND template_code = "
                    + "'OVERSTOCK' AND subject = ?", String.class, uid("ADMIN"), "Too much: " + entityId))
                    .isEqualTo("Custom text. Bin A is over its maximum");
            assertThat(emailCount("ADMIN", "OVERSTOCK")).as("the template is now IN_APP only").isZero();

            long active = inAppCount("ADMIN", "OVERSTOCK");
            sendJson("ADMIN", put("/api/notification-templates/{id}", id), edit.formatted("false"));
            raise("OVERSTOCK", "LOW", "Bin B is over its maximum");
            assertThat(inAppCount("ADMIN", "OVERSTOCK")).as("an inactive template sends nothing").isEqualTo(active);
        } finally {
            sendJson("ADMIN", put("/api/notification-templates/{id}", id),
                    ("{\"channel\":\"BOTH\",\"description\":\"Alert: stock above its maximum level\","
                            + "\"subjectTemplate\":\"[{{severity}}] Overstock\",\"bodyTemplate\":\"{{message}}\","
                            + "\"active\":true}"));
        }
        assertThat(status(sendJson("ADMIN", put("/api/notification-templates/{id}", id),
                "{\"channel\":\"TELEGRAM\",\"bodyTemplate\":\"x\"}"))).isEqualTo(400);
        assertThat(status(sendJson("ADMIN", put("/api/notification-templates/{id}", 987654321L),
                "{\"channel\":\"BOTH\",\"bodyTemplate\":\"x\"}"))).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = "
                + "'NOTIFICATION_TEMPLATE_UPDATED' AND entity_id = ?", Long.class, String.valueOf(id)))
                .isGreaterThanOrEqualTo(3L);
    }

    @Test
    void templatesAndTheDeliveryLogAreClosedToOrdinaryUsers() throws Exception {
        assertThat(status(call("QUALITY_MANAGER", get("/api/notification-templates")))).isEqualTo(403);
        assertThat(status(call("QUALITY_MANAGER", get("/api/notification-log")))).isEqualTo(403);
        assertThat(status(mockMvc.perform(get("/api/notifications")).andReturn())).isEqualTo(401);
    }

    @Test
    void templateTextCannotReadAnythingBeyondThePayload() {
        long id = raise("AGING_BATCH", "LOW", "Message with {{secret}} and ${user.home}");
        String body = jdbc.queryForObject("SELECT body FROM notifications WHERE user_id = ? AND dedup_key = ?",
                String.class, uid("ADMIN"), "ALERT:" + id + ":IN_APP:" + uid("ADMIN"));
        assertThat(body).isEqualTo("Message with {{secret}} and ${user.home}");
    }
}
