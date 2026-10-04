package com.springmfg.ims.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.springmfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.springmfg.ims.document.service.DocumentLinkTargets;
import com.springmfg.ims.iam.dto.CreateUserRequest;
import com.springmfg.ims.iam.service.UserAdminService;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.TestData;

/**
 * Documents end to end against real PostgreSQL and a real file store: upload, versions, links, per-link access,
 * categories that demand extra authority, the approval state machine, segregation of duties, upload safety and the
 * audit trail. Callers are real users holding exactly one seeded role, authenticated by the real login.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Docs#Test-2026";
    private static final List<String> ROLES = List.of("ADMIN", "ENGINEER", "QUALITY_MANAGER", "PURCHASE_MANAGER",
            "MANAGEMENT", "OPERATOR");
    private static final byte[] PDF = ("%PDF-1.7\nspring wire drawing\n").getBytes(StandardCharsets.UTF_8);

    @Autowired UserAdminService userAdminService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData td;
    @Autowired DocumentLinkTargets linkTargets;
    @Autowired CacheManager cacheManager;
    @Value("${ims.documents.storage-root}") String storageRoot;

    private final Map<String, String> tokens = new LinkedHashMap<>();
    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    void createUsersAndLogIn() throws Exception {
        var actor = new CachedUser(9002L, "doc-setup", true, 1, Set.of(), Set.of("USER_CREATE"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("USER_CREATE"))));
        try {
            for (String role : ROLES) {
                String username = username(role);
                userAdminService.create(new CreateUserRequest(username, null, "DOC " + role,
                        username + "@docs.test", null, PASSWORD, Set.of(role)));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        for (String role : ROLES) {
            MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username(role) + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .andReturn();
            assertThat(login.getResponse().getStatus()).as("login as %s", role).isEqualTo(200);
            tokens.put(role, JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"));
        }
    }

    @AfterAll
    void removeTheUsers() {
        String like = "doc_%_" + runId;
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM password_history WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username LIKE ?)", like);
        jdbc.update("UPDATE system_settings SET value = '25' WHERE key = 'documents.max_size_mb'");
        cacheManager.getCache("systemSettings").clear();
    }

    private String username(String role) {
        return "doc_" + role.toLowerCase() + "_" + runId;
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private MvcResult upload(String role, String category, String title, String linksJson, String revision,
                             String filename, byte[] content) throws Exception {
        String meta = "{\"categoryCode\":\"" + category + "\",\"title\":\"" + title + "\""
                + (revision == null ? "" : ",\"revisionLabel\":\"" + revision + "\"")
                + (linksJson == null ? "" : ",\"links\":" + linksJson) + "}";
        return mockMvc.perform(multipart("/api/documents")
                .file(new MockMultipartFile("meta", "", MediaType.APPLICATION_JSON_VALUE,
                        meta.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(role))).andReturn();
    }

    private MvcResult addVersion(String role, long id, String revision, String filename, byte[] content)
            throws Exception {
        String meta = "{\"revisionLabel\":\"" + revision + "\",\"changeNote\":\"change " + revision + "\"}";
        return mockMvc.perform(multipart("/api/documents/{id}/versions", id)
                .file(new MockMultipartFile("meta", "", MediaType.APPLICATION_JSON_VALUE,
                        meta.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(role))).andReturn();
    }

    private MvcResult call(String role, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b)
            throws Exception {
        return mockMvc.perform(b.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.get(role))).andReturn();
    }

    private MvcResult postJson(String role, String path, String body) throws Exception {
        return call(role, post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    private static <T> T json(MvcResult r, String path) throws Exception {
        return JsonPath.read(r.getResponse().getContentAsString(), path);
    }

    private long uploadOk(String role, String category, String title, String linksJson, String filename,
                          byte[] content) throws Exception {
        MvcResult r = upload(role, category, title, linksJson, null, filename, content);
        assertThat(status(r)).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return ((Number) json(r, "$.id")).longValue();
    }

    private int auditCount(String action, long documentId) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND entity = 'Document' "
                + "AND entity_id = ?", Integer.class, action, String.valueOf(documentId));
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static String productLink(long id) {
        return "[{\"entityType\":\"PRODUCT\",\"entityId\":" + id + "}]";
    }

    // ---- tests ----------------------------------------------------------------------------------------------------

    @Test
    void uploadLinkAndDownloadRoundTripTheContentAndAreAudited() throws Exception {
        long productId = td.product().getId();
        MvcResult created = upload("ENGINEER", "SOP", "Coiling SOP", productLink(productId), "A", "coiling sop.pdf",
                PDF);

        assertThat(status(created)).isEqualTo(201);
        long id = ((Number) json(created, "$.id")).longValue();
        assertThat(json(created, "$.documentNumber").toString()).startsWith("DOC");
        assertThat(json(created, "$.status").toString()).isEqualTo("DRAFT");
        assertThat(json(created, "$.categoryCode").toString()).isEqualTo("SOP");
        assertThat(((Number) json(created, "$.currentVersionNo")).intValue()).isEqualTo(1);
        assertThat(json(created, "$.versions[0].revisionLabel").toString()).isEqualTo("A");
        assertThat(json(created, "$.versions[0].originalFilename").toString()).isEqualTo("coiling sop.pdf");
        assertThat(json(created, "$.versions[0].mimeType").toString()).isEqualTo("application/pdf");
        assertThat(((Number) json(created, "$.versions[0].sizeBytes")).longValue()).isEqualTo(PDF.length);
        assertThat(json(created, "$.versions[0].sha256").toString()).isEqualTo(sha256(PDF));
        assertThat(json(created, "$.links[0].entityType").toString()).isEqualTo("PRODUCT");
        assertThat(((Number) json(created, "$.links[0].entityId")).longValue()).isEqualTo(productId);
        assertThat(auditCount("DOCUMENT_UPLOADED", id)).isEqualTo(1);

        MvcResult download = call("ENGINEER", get("/api/documents/{id}/download", id));
        assertThat(status(download)).isEqualTo(200);
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertThat(download.getResponse().getContentType()).isEqualTo("application/pdf");
        ContentDisposition disposition = ContentDisposition.parse(
                download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposition.isAttachment()).isTrue();
        assertThat(disposition.getFilename()).isEqualTo("coiling sop.pdf");
        assertThat(download.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(download.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(auditCount("DOCUMENT_DOWNLOADED", id)).isEqualTo(1);

        call("QUALITY_MANAGER", get("/api/documents/{id}/download", id));
        assertThat(auditCount("DOCUMENT_DOWNLOADED", id)).as("every download is audited").isEqualTo(2);
    }

    @Test
    void aDocumentCanBeAttachedToABomAndLinkedAndUnlinkedLater() throws Exception {
        long productId = td.product().getId();
        long bomId = td.draftBom(productId).getId();
        long id = uploadOk("ADMIN", "OTHER", "Winding notes", null, "notes.txt", "winding notes".getBytes());

        MvcResult linked = postJson("ADMIN", "/api/documents/" + id + "/links",
                "{\"entityType\":\"BOM\",\"entityId\":" + bomId + "}");
        assertThat(status(linked)).isEqualTo(200);
        assertThat(json(linked, "$.links[0].entityType").toString()).isEqualTo("BOM");
        long linkId = ((Number) json(linked, "$.links[0].id")).longValue();
        assertThat(auditCount("DOCUMENT_LINKED", id)).isEqualTo(1);

        MvcResult byEntity = call("ADMIN", get("/api/documents").param("entityType", "BOM")
                .param("entityId", String.valueOf(bomId)));
        assertThat(status(byEntity)).isEqualTo(200);
        assertThat(JsonPath.<List<Integer>>read(byEntity.getResponse().getContentAsString(), "$.content[*].id"))
                .containsExactly((int) id);

        MvcResult unlinked = call("ADMIN", delete("/api/documents/{id}/links/{linkId}", id, linkId));
        assertThat(status(unlinked)).isEqualTo(200);
        assertThat(JsonPath.<List<Object>>read(unlinked.getResponse().getContentAsString(), "$.links")).isEmpty();
        assertThat(auditCount("DOCUMENT_UNLINKED", id)).isEqualTo(1);
    }

    @Test
    void aNewVersionKeepsTheHistoryAndSendsAnApprovedDocumentBackToDraft() throws Exception {
        byte[] v1 = "%PDF-1.7\nrev A\n".getBytes(StandardCharsets.UTF_8);
        byte[] v2 = "%PDF-1.7\nrev B with a change\n".getBytes(StandardCharsets.UTF_8);
        MvcResult created = upload("ENGINEER", "DRAWING", "Spring drawing", null, "A", "drawing.pdf", v1);
        assertThat(status(created)).isEqualTo(201);
        long id = ((Number) json(created, "$.id")).longValue();

        assertThat(status(call("ENGINEER", post("/api/documents/{id}/submit", id)))).isEqualTo(200);
        MvcResult approved = call("QUALITY_MANAGER", post("/api/documents/{id}/approve", id));
        assertThat(status(approved)).isEqualTo(200);
        assertThat(json(approved, "$.status").toString()).isEqualTo("APPROVED");
        assertThat(((Number) json(approved, "$.approvedBy")).longValue()).isPositive();

        MvcResult revised = addVersion("ENGINEER", id, "B", "drawing.pdf", v2);
        assertThat(status(revised)).isEqualTo(201);
        assertThat(json(revised, "$.status").toString()).isEqualTo("DRAFT");
        assertThat(((Number) json(revised, "$.currentVersionNo")).intValue()).isEqualTo(2);
        assertThat(JsonPath.<List<Integer>>read(revised.getResponse().getContentAsString(), "$.versions[*].versionNo"))
                .containsExactly(2, 1);
        assertThat(json(revised, "$.versions[0].revisionLabel").toString()).isEqualTo("B");
        assertThat(json(revised, "$.versions[1].revisionLabel").toString()).isEqualTo("A");
        assertThat(revised.getResponse().getContentAsString()).as("approval is cleared")
                .doesNotContain("\"approvedBy\"");
        assertThat(auditCount("DOCUMENT_VERSION_ADDED", id)).isEqualTo(1);
        assertThat(auditCount("DOCUMENT_RETURNED_TO_DRAFT", id)).isEqualTo(1);

        assertThat(call("ENGINEER", get("/api/documents/{id}/download", id)).getResponse().getContentAsByteArray())
                .as("the current version").isEqualTo(v2);
        assertThat(call("ENGINEER", get("/api/documents/{id}/download", id).param("version", "1"))
                .getResponse().getContentAsByteArray()).as("history stays downloadable").isEqualTo(v1);
        assertThat(status(call("ENGINEER", get("/api/documents/{id}/download", id).param("version", "9"))))
                .isEqualTo(404);
    }

    @Test
    void theApproverMustNotBeTheUploaderOfTheCurrentVersion() throws Exception {
        long id = uploadOk("QUALITY_MANAGER", "SOP", "Inspection SOP", null, "inspection.pdf", PDF);
        assertThat(status(call("QUALITY_MANAGER", post("/api/documents/{id}/submit", id)))).isEqualTo(200);

        MvcResult own = call("QUALITY_MANAGER", post("/api/documents/{id}/approve", id));
        assertThat(status(own)).isEqualTo(422);
        assertThat(json(own, "$.code").toString()).isEqualTo("SEGREGATION_OF_DUTIES");

        assertThat(status(call("ADMIN", post("/api/documents/{id}/approve", id)))).isEqualTo(200);
        assertThat(auditCount("DOCUMENT_APPROVED", id)).isEqualTo(1);
    }

    @Test
    void theStateMachineRefusesIllegalMovesAndAReasonIsMandatoryForReturnAndObsolete() throws Exception {
        long id = uploadOk("ENGINEER", "SOP", "Packing SOP", null, "packing.pdf", PDF);

        MvcResult early = call("ADMIN", post("/api/documents/{id}/approve", id));
        assertThat(status(early)).as("DRAFT cannot be approved").isEqualTo(422);
        assertThat(json(early, "$.code").toString()).isEqualTo("ILLEGAL_STATE_TRANSITION");

        assertThat(status(call("ENGINEER", post("/api/documents/{id}/submit", id)))).isEqualTo(200);
        assertThat(status(postJson("QUALITY_MANAGER", "/api/documents/" + id + "/reject", "{}"))).isEqualTo(400);
        MvcResult returned = postJson("QUALITY_MANAGER", "/api/documents/" + id + "/reject",
                "{\"reason\":\"Missing torque value\"}");
        assertThat(status(returned)).isEqualTo(200);
        assertThat(json(returned, "$.status").toString()).isEqualTo("DRAFT");
        assertThat(json(returned, "$.statusReason").toString()).isEqualTo("Missing torque value");

        assertThat(status(postJson("QUALITY_MANAGER", "/api/documents/" + id + "/obsolete", "{\"reason\":\" \"}")))
                .isEqualTo(400);
        MvcResult obsolete = postJson("QUALITY_MANAGER", "/api/documents/" + id + "/obsolete",
                "{\"reason\":\"Replaced by SOP-12\"}");
        assertThat(json(obsolete, "$.status").toString()).isEqualTo("OBSOLETE");

        assertThat(status(call("ENGINEER", post("/api/documents/{id}/submit", id)))).isEqualTo(422);
        assertThat(status(addVersion("ENGINEER", id, "C", "packing.pdf", PDF))).as("no version on OBSOLETE")
                .isEqualTo(422);
    }

    @Test
    void categoriesThatNameAnAuthorityDemandItForUploadsVersionsAndLinks() throws Exception {
        // PURCHASE_MANAGER may upload documents but holds neither DRAWING_MANAGE nor SPECIFICATION_MANAGE
        assertThat(status(upload("PURCHASE_MANAGER", "DRAWING", "Supplier drawing", null, null, "d.pdf", PDF)))
                .isEqualTo(403);
        assertThat(status(upload("PURCHASE_MANAGER", "SPECIFICATION", "Spec", null, null, "s.pdf", PDF)))
                .isEqualTo(403);
        assertThat(status(upload("PURCHASE_MANAGER", "CONTRACT", "Supply contract", null, null, "c.pdf", PDF)))
                .isEqualTo(201);

        // ENGINEER holds DRAWING_MANAGE but not SPECIFICATION_MANAGE (that stays with PLM engineers)
        long drawing = uploadOk("ENGINEER", "DRAWING", "Engineering drawing", null, "e.pdf", PDF);
        assertThat(status(upload("ENGINEER", "SPECIFICATION", "Spec change", null, null, "s.pdf", PDF)))
                .isEqualTo(403);
        assertThat(status(upload("ADMIN", "SPECIFICATION", "Spec change", null, null, "s.pdf", PDF)))
                .isEqualTo(201);

        // the extra authority also guards later changes to an existing drawing
        assertThat(status(addVersion("PURCHASE_MANAGER", drawing, "B", "e.pdf", PDF))).isEqualTo(403);
        assertThat(status(call("PURCHASE_MANAGER", get("/api/documents/{id}", drawing)))).isEqualTo(200);
    }

    @Test
    void aLinkedDocumentIsReadableOnlyByThoseWhoCanReadWhatItIsLinkedTo() throws Exception {
        long supplierId = td.supplier().getId();
        long id = uploadOk("ADMIN", "CERTIFICATE", "Material certificate",
                "[{\"entityType\":\"SUPPLIER\",\"entityId\":" + supplierId + "}]", "cert.pdf", PDF);
        long unlinked = uploadOk("ADMIN", "MANUAL", "Machine manual", null, "manual.pdf", PDF);

        // PURCHASE_MANAGER reads suppliers; ENGINEER does not
        assertThat(status(call("PURCHASE_MANAGER", get("/api/documents/{id}", id)))).isEqualTo(200);
        assertThat(status(call("PURCHASE_MANAGER", get("/api/documents/{id}/download", id)))).isEqualTo(200);
        MvcResult denied = call("ENGINEER", get("/api/documents/{id}", id));
        assertThat(status(denied)).isEqualTo(403);
        assertThat(status(call("ENGINEER", get("/api/documents/{id}/download", id)))).isEqualTo(403);
        assertThat(status(call("ENGINEER", post("/api/documents/{id}/submit", id)))).isEqualTo(403);
        assertThat(status(addVersion("ENGINEER", id, "B", "cert.pdf", PDF))).isEqualTo(403);

        List<Integer> engineerSees = JsonPath.read(call("ENGINEER", get("/api/documents").param("size", "200"))
                .getResponse().getContentAsString(), "$.content[*].id");
        assertThat(engineerSees).contains((int) unlinked).doesNotContain((int) id);
        List<Integer> purchaseSees = JsonPath.read(call("PURCHASE_MANAGER", get("/api/documents")
                .param("size", "200")).getResponse().getContentAsString(), "$.content[*].id");
        assertThat(purchaseSees).contains((int) id, (int) unlinked);

        // DOCUMENT_MANAGE reads everything regardless of links
        assertThat(status(call("ADMIN", get("/api/documents/{id}", id)))).isEqualTo(200);
        // someone without DOCUMENT_VIEW is stopped at the endpoint
        assertThat(status(call("OPERATOR", get("/api/documents/{id}", unlinked)))).isEqualTo(403);
    }

    @Test
    void linkingNeedsAnExistingRecordOfASupportedTypeThatTheCallerMayRead() throws Exception {
        long id = uploadOk("ENGINEER", "OTHER", "Misc", null, "misc.txt", "x".getBytes());
        long supplierId = td.supplier().getId();

        assertThat(status(postJson("ENGINEER", "/api/documents/" + id + "/links",
                "{\"entityType\":\"SUPPLIER\",\"entityId\":" + supplierId + "}")))
                .as("ENGINEER cannot read suppliers").isEqualTo(403);
        assertThat(status(postJson("ENGINEER", "/api/documents/" + id + "/links",
                "{\"entityType\":\"PRODUCT\",\"entityId\":987654321}"))).isEqualTo(404);
        MvcResult unsupported = postJson("ENGINEER", "/api/documents/" + id + "/links",
                "{\"entityType\":\"WAREHOUSE\",\"entityId\":1}");
        assertThat(status(unsupported)).isEqualTo(400);

        long productId = td.product().getId();
        String body = "{\"entityType\":\"product\",\"entityId\":" + productId + "}";
        assertThat(status(postJson("ENGINEER", "/api/documents/" + id + "/links", body))).isEqualTo(200);
        MvcResult duplicate = postJson("ENGINEER", "/api/documents/" + id + "/links", body);
        assertThat(status(duplicate)).isEqualTo(409);
        assertThat(json(duplicate, "$.code").toString()).isEqualTo("DUPLICATE_KEY");
    }

    @Test
    void capaEvidenceCanBeAttachedAndIsVisibleToQualityOnly() throws Exception {
        MvcResult capa = postJson("QUALITY_MANAGER", "/api/capa",
                "{\"title\":\"Coil diameter drift\",\"description\":\"audit finding\","
                        + "\"sourceType\":\"INTERNAL_AUDIT\"}");
        assertThat(status(capa)).as(capa.getResponse().getContentAsString()).isEqualTo(201);
        long capaId = ((Number) json(capa, "$.id")).longValue();

        long id = uploadOk("QUALITY_MANAGER", "CAPA_EVIDENCE", "Measurement record",
                "[{\"entityType\":\"CAPA\",\"entityId\":" + capaId + "}]", "evidence.pdf", PDF);

        MvcResult forCapa = call("QUALITY_MANAGER", get("/api/documents").param("entityType", "CAPA")
                .param("entityId", String.valueOf(capaId)));
        assertThat(JsonPath.<List<Integer>>read(forCapa.getResponse().getContentAsString(), "$.content[*].id"))
                .containsExactly((int) id);
        assertThat(status(call("QUALITY_MANAGER", get("/api/documents/{id}/download", id)))).isEqualTo(200);
        // ENGINEER cannot read quality records, so the linked evidence is closed to them
        assertThat(status(call("ENGINEER", get("/api/documents/{id}", id)))).isEqualTo(403);
    }

    @Test
    void searchFiltersWorkWithAndWithoutEachFilter() throws Exception {
        String title = "Searchable " + runId;
        long id = uploadOk("ENGINEER", "SOP", title, null, "s.pdf", PDF);

        assertThat(ids(call("ENGINEER", get("/api/documents").param("size", "200")))).contains((int) id);
        assertThat(ids(call("ENGINEER", get("/api/documents").param("q", "   ")))).as("blank q is no filter")
                .isNotEmpty();
        assertThat(ids(call("ENGINEER", get("/api/documents").param("q", title.toUpperCase()))))
                .containsExactly((int) id);
        assertThat(ids(call("ENGINEER", get("/api/documents").param("q", "no such document anywhere")))).isEmpty();
        assertThat(ids(call("ENGINEER", get("/api/documents").param("q", title).param("category", "sop"))))
                .containsExactly((int) id);
        assertThat(ids(call("ENGINEER", get("/api/documents").param("q", title).param("category", "DRAWING"))))
                .isEmpty();
        assertThat(ids(call("ENGINEER", get("/api/documents").param("category", "NO_SUCH_CATEGORY")))).isEmpty();
        assertThat(ids(call("ENGINEER", get("/api/documents").param("q", title).param("status", "DRAFT"))))
                .containsExactly((int) id);
        assertThat(ids(call("ENGINEER", get("/api/documents").param("q", title).param("status", "APPROVED"))))
                .isEmpty();
        assertThat(ids(call("ENGINEER", get("/api/documents").param("entityType", "PRODUCT")
                .param("entityId", "987654321")))).isEmpty();
    }

    private static List<Integer> ids(MvcResult r) throws Exception {
        assertThat(status(r)).isEqualTo(200);
        return JsonPath.read(r.getResponse().getContentAsString(), "$.content[*].id");
    }

    // ---- upload safety --------------------------------------------------------------------------------------------

    @Test
    void filesOfAForbiddenTypeOrWithMismatchedContentAreRefused() throws Exception {
        MvcResult exe = upload("ENGINEER", "OTHER", "Program", null, null, "setup.exe", "MZ".getBytes());
        assertThat(status(exe)).isEqualTo(415);
        assertThat(json(exe, "$.code").toString()).isEqualTo("FILE_TYPE_NOT_ALLOWED");

        MvcResult disguised = upload("ENGINEER", "OTHER", "Disguised", null, null, "invoice.pdf",
                "MZ not a pdf".getBytes());
        assertThat(status(disguised)).isEqualTo(415);

        MvcResult empty = upload("ENGINEER", "OTHER", "Empty", null, null, "empty.pdf", new byte[0]);
        assertThat(status(empty)).isEqualTo(400);

        assertThat(status(upload("ENGINEER", "NO_SUCH_CATEGORY", "x", null, null, "x.pdf", PDF))).isEqualTo(404);
        assertThat(status(upload("ENGINEER", "SOP", " ", null, null, "x.pdf", PDF))).isEqualTo(400);
    }

    @Test
    void oversizedFilesAreRefusedAccordingToTheSetting() throws Exception {
        setMaxSizeMb("1");
        try {
            byte[] big = new byte[1024 * 1024 + 1];
            System.arraycopy(PDF, 0, big, 0, PDF.length);
            MvcResult tooBig = upload("ENGINEER", "OTHER", "Big", null, null, "big.pdf", big);
            assertThat(status(tooBig)).isEqualTo(413);
            assertThat(json(tooBig, "$.code").toString()).isEqualTo("FILE_TOO_LARGE");

            byte[] fits = new byte[1024 * 1024];
            System.arraycopy(PDF, 0, fits, 0, PDF.length);
            assertThat(status(upload("ENGINEER", "OTHER", "Fits", null, null, "fits.pdf", fits))).isEqualTo(201);
        } finally {
            setMaxSizeMb("25");
        }
    }

    @Test
    void aClientSuppliedPathInTheFilenameNeverReachesTheFileSystem() throws Exception {
        MvcResult r = upload("ENGINEER", "OTHER", "Traversal", null, null, "../../outside/evil.pdf", PDF);
        assertThat(status(r)).isEqualTo(201);
        long id = ((Number) json(r, "$.id")).longValue();
        assertThat(json(r, "$.versions[0].originalFilename").toString()).isEqualTo("evil.pdf");

        String key = jdbc.queryForObject("SELECT storage_key FROM document_versions WHERE document_id = ?",
                String.class, id);
        assertThat(key).matches("\\d{4}/\\d{2}/[0-9a-f-]{36}").doesNotContain("evil").doesNotContain("..");
        Path root = Path.of(storageRoot).toAbsolutePath().normalize();
        assertThat(Files.isRegularFile(root.resolve(key))).isTrue();
        assertThat(Files.exists(root.resolve("evil.pdf"))).isFalse();
        assertThat(Files.exists(root.getParent().resolve("outside"))).isFalse();

        MvcResult download = call("ENGINEER", get("/api/documents/{id}/download", id));
        assertThat(ContentDisposition.parse(download.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .getFilename()).isEqualTo("evil.pdf");
    }

    @Test
    void aMissingStoredFileAnswersNotFoundRatherThanAServerError() throws Exception {
        long id = uploadOk("ENGINEER", "OTHER", "Vanishing", null, "gone.pdf", PDF);
        String key = jdbc.queryForObject("SELECT storage_key FROM document_versions WHERE document_id = ?",
                String.class, id);
        Files.delete(Path.of(storageRoot).toAbsolutePath().normalize().resolve(key));

        assertThat(status(call("ENGINEER", get("/api/documents/{id}/download", id)))).isEqualTo(404);
        assertThat(auditCount("DOCUMENT_DOWNLOADED", id)).as("a failed download is not audited as a download")
                .isZero();
    }

    @Test
    void unauthenticatedAndUnknownRequestsAreRejected() throws Exception {
        assertThat(status(mockMvc.perform(get("/api/documents")).andReturn())).isEqualTo(401);
        assertThat(status(mockMvc.perform(get("/api/documents/1/download")).andReturn())).isEqualTo(401);
        assertThat(status(call("ADMIN", get("/api/documents/{id}", 987654321L)))).isEqualTo(404);
        assertThat(status(call("ADMIN", get("/api/documents/{id}/download", 987654321L)))).isEqualTo(404);
    }

    // ---- categories and wiring ------------------------------------------------------------------------------------

    @Test
    void categoriesAreSeededAndOnlyDocumentManagersMayChangeThem() throws Exception {
        MvcResult list = call("ENGINEER", get("/api/document-categories"));
        assertThat(status(list)).isEqualTo(200);
        assertThat(JsonPath.<List<String>>read(list.getResponse().getContentAsString(), "$[*].categoryCode"))
                .contains("DRAWING", "SPECIFICATION", "SOP", "CERTIFICATE", "CONTRACT", "CAPA_EVIDENCE", "MANUAL",
                        "OTHER");
        assertThat(JsonPath.<List<String>>read(list.getResponse().getContentAsString(),
                "$[?(@.categoryCode=='DRAWING')].requiredAuthority")).containsExactly("DRAWING_MANAGE");

        String code = ("T" + runId).toUpperCase();
        String body = "{\"categoryCode\":\"" + code + "\",\"name\":\"Test category\",\"retentionYears\":3}";
        assertThat(status(postJson("ENGINEER", "/api/document-categories", body))).isEqualTo(403);
        MvcResult created = postJson("ADMIN", "/api/document-categories", body);
        assertThat(status(created)).isEqualTo(201);
        assertThat(status(postJson("ADMIN", "/api/document-categories", body))).as("duplicate code")
                .isEqualTo(400);
        assertThat(status(upload("ENGINEER", code, "In the new category", null, null, "n.pdf", PDF))).isEqualTo(201);
    }

    @Test
    void everyAuthorityUsedToDecideLinkAccessIsASeededPermission() {
        List<String> missing = new ArrayList<>();
        linkTargets.readAuthorities().forEach((type, codes) -> codes.forEach(code -> {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM permissions WHERE code = ?", Integer.class, code);
            if (n == null || n == 0) {
                missing.add(type + " -> " + code);
            }
        }));
        assertThat(missing).as("link access codes missing from the permission catalogue").isEmpty();
        assertThat(linkTargets.readAuthorities().keySet()).containsExactlyInAnyOrderElementsOf(
                jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conname = 'chk_doclink_type'", String.class).stream()
                        .flatMap(def -> java.util.regex.Pattern.compile("'([A-Z]+)'").matcher(def).results())
                        .map(m -> m.group(1)).toList());
    }

    private void setMaxSizeMb(String value) {
        jdbc.update("UPDATE system_settings SET value = ? WHERE key = 'documents.max_size_mb'", value);
        cacheManager.getCache("systemSettings").clear();
    }
}
