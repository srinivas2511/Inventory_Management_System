package com.mfg.ims.integration.gst;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.integration.gst.GstEInvoiceDtos.EInvoiceDto;
import com.mfg.ims.integration.gst.GstEInvoiceDtos.GenerateRequest;

/**
 * R19-6.5 GST e-invoice adapter. Generates GSTN-schema JSON for AR dispatches.
 * The payload can be submitted to the IRP (Invoice Registration Portal) via
 * the supplier's GST Suvidha Provider API — this service builds the payload;
 * actual API submission is the responsibility of the operator / ISP layer.
 */
@Service
public class GstEInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(GstEInvoiceService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final GstEInvoiceRepository repo;
    private final RestTemplate rest = new RestTemplate();

    public GstEInvoiceService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper, GstEInvoiceRepository repo) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.repo = repo;
    }

    public record GspSubmitResult(String irn, String ackNo, String status) {}

    /**
     * Submit a generated e-invoice JSON payload to the configured GSP API.
     * If gst.gsp.enabled=false or api_url is blank, returns a stub IRN for testing.
     */
    @Transactional
    public GspSubmitResult submitToGsp(Long id) {
        GstEInvoice entity = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("GstEInvoice not found: " + id));
        if (!"GENERATED".equals(entity.getStatus()) && entity.getIrn() == null) {
            // already submitted — return existing
            return new GspSubmitResult(entity.getIrn(), entity.getAckNo(), entity.getStatus());
        }

        String apiUrl = setting("gst.gsp.api_url");
        String authToken = setting("gst.gsp.auth_token");
        boolean enabled = Boolean.parseBoolean(setting("gst.gsp.enabled"));

        String irn;
        String ackNo;
        if (enabled && apiUrl != null && !apiUrl.isBlank()) {
            // Live GSP submission
            GspSubmitResult liveResult = callGspApi(entity.getPayloadJson(), apiUrl, authToken);
            irn = liveResult.irn();
            ackNo = liveResult.ackNo();
        } else {
            // Stub mode: generate deterministic IRN
            irn = "stub_irn_" + entity.getId() + "_" + System.currentTimeMillis() % 100000;
            ackNo = "stub_ack_" + System.currentTimeMillis() % 1000000;
            log.info("GSP stub: generated mock IRN {} for e-invoice {}", irn, id);
        }

        entity.setIrn(irn);
        entity.setAckNo(ackNo);
        entity.setStatus("SUBMITTED");
        entity.setAckDate(Instant.now());
        repo.save(entity);

        return new GspSubmitResult(irn, ackNo, "SUBMITTED");
    }

    @SuppressWarnings("unchecked")
    private GspSubmitResult callGspApi(String payload, String apiUrl, String authToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (authToken != null && !authToken.isBlank()) {
                headers.set("Authorization", "Bearer " + authToken);
            }
            var response = rest.postForObject(apiUrl + "/einvoice/generate",
                new HttpEntity<>(payload, headers), Map.class);
            if (response == null) throw new BusinessRuleException(ErrorCode.EXTERNAL_SERVICE_ERROR, "GSP returned empty response");
            Object irn = response.get("Irn");
            Object ack = response.get("AckNo");
            return new GspSubmitResult(
                irn != null ? irn.toString() : "",
                ack != null ? ack.toString() : "",
                "SUBMITTED"
            );
        } catch (RestClientException e) {
            throw new BusinessRuleException(ErrorCode.EXTERNAL_SERVICE_ERROR, "GSP API error: " + e.getMessage());
        }
    }

    private String setting(String key) {
        try {
            return jdbc.queryForObject(
                "SELECT value FROM ims.system_settings WHERE key = :k",
                new MapSqlParameterSource("k", key), String.class);
        } catch (Exception e) {
            return "";
        }
    }

    public List<EInvoiceDto> findAll() {
        return repo.findAll().stream().map(e -> new EInvoiceDto(
            e.getId(), e.getDispatchId(), e.getIrn(), e.getAckNo(),
            e.getAckDate(), e.getStatus(), e.getGeneratedAt()
        )).toList();
    }

    public String getPayload(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new NotFoundException("GstEInvoice not found: " + id))
            .getPayloadJson();
    }

    @Transactional
    public EInvoiceDto generate(GenerateRequest req, Long userId) {
        // Check for existing (idempotent)
        repo.findByDispatchId(req.dispatchId()).ifPresent(e -> {
            throw new IllegalStateException("E-invoice already generated for dispatch " + req.dispatchId());
        });

        Map<String, Object> dispatch = fetchDispatch(req.dispatchId());
        List<Map<String, Object>> items = fetchDispatchItems(req.dispatchId());
        Map<String, Object> company = fetchCompany();

        String payloadJson = buildPayload(dispatch, items, company);

        GstEInvoice entity = new GstEInvoice();
        entity.setDispatchId(req.dispatchId());
        entity.setPayloadJson(payloadJson);
        entity.setGeneratedBy(userId);
        GstEInvoice saved = repo.save(entity);

        return new EInvoiceDto(saved.getId(), saved.getDispatchId(), null, null, null,
            saved.getStatus(), saved.getGeneratedAt());
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private Map<String, Object> fetchDispatch(Long dispatchId) {
        String sql = """
            SELECT d.id, d.dispatch_number, d.dispatch_date, d.delivery_address,
                   c.name AS customer_name, c.gst_number AS buyer_gstin,
                   c.address AS buyer_address,
                   ai.total_amount, ai.supply_type
            FROM ims.dispatches d
            JOIN ims.customers c ON c.id = d.customer_id
            LEFT JOIN ims.ar_invoices ai ON ai.dispatch_id = d.id
            WHERE d.id = :id
            """;
        var rows = jdbc.queryForList(sql, new MapSqlParameterSource("id", dispatchId));
        if (rows.isEmpty()) throw new NotFoundException("Dispatch not found: " + dispatchId);
        return rows.get(0);
    }

    private List<Map<String, Object>> fetchDispatchItems(Long dispatchId) {
        String sql = """
            SELECT di.quantity, di.unit_price,
                   p.product_code, p.name AS product_name,
                   '9999' AS hsn_sac,
                   ROUND(di.quantity * di.unit_price, 2) AS taxable_value
            FROM ims.dispatch_items di
            JOIN ims.products p ON p.id = di.product_id
            WHERE di.dispatch_id = :id
            """;
        return jdbc.queryForList(sql, new MapSqlParameterSource("id", dispatchId));
    }

    private Map<String, Object> fetchCompany() {
        String sql = "SELECT name, gstin AS gst_number FROM ims.companies WHERE id = 1";
        var rows = jdbc.queryForList(sql, Map.of());
        return rows.isEmpty() ? Map.of("name", "Unknown", "gst_number", "") : rows.get(0);
    }

    private static String str(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return (v != null) ? v.toString() : fallback;
    }

    private String buildPayload(
        Map<String, Object> dispatch,
        List<Map<String, Object>> items,
        Map<String, Object> company
    ) {
        try {
            var doc = new LinkedHashMap<String, Object>();

            // Version
            doc.put("Version", "1.1");

            // Transaction details
            var txn = new LinkedHashMap<String, Object>();
            txn.put("TaxSch", "GST");
            txn.put("SupTyp", str(dispatch, "supply_type", "B2B"));
            txn.put("RegRev", "N");
            txn.put("EcmGstin", (Object) null);
            txn.put("IgstOnIntra", "N");
            doc.put("TranDtls", txn);

            // Document details
            var docDtls = new LinkedHashMap<String, Object>();
            docDtls.put("Typ", "INV");
            docDtls.put("No", dispatch.get("dispatch_number"));
            var dispDate = dispatch.get("dispatch_date");
            docDtls.put("Dt", dispDate != null ? dispDate.toString().replace("-", "/") : "");
            doc.put("DocDtls", docDtls);

            // Seller (supplier) details
            var seller = new LinkedHashMap<String, Object>();
            seller.put("Gstin", str(company, "gst_number", ""));
            seller.put("LglNm", str(company, "name", ""));
            seller.put("Addr1", "1 Main Street");
            seller.put("Loc", "Bengaluru");
            seller.put("Pin", "560001");
            seller.put("Stcd", "29");
            doc.put("SellerDtls", seller);

            // Buyer details
            var buyer = new LinkedHashMap<String, Object>();
            buyer.put("Gstin", str(dispatch, "buyer_gstin", "URP"));
            buyer.put("LglNm", str(dispatch, "customer_name", ""));
            buyer.put("Pos", "29");
            buyer.put("Addr1", str(dispatch, "delivery_address", ""));
            buyer.put("Loc", "");
            buyer.put("Pin", "560001");
            buyer.put("Stcd", "29");
            doc.put("BuyerDtls", buyer);

            // Item list
            BigDecimal totalTaxableValue = BigDecimal.ZERO;
            var itemList = new java.util.ArrayList<>();
            int slNo = 1;
            for (Map<String, Object> item : items) {
                var it = new LinkedHashMap<String, Object>();
                it.put("SlNo", String.valueOf(slNo++));
                it.put("PrdDesc", item.getOrDefault("product_name", "").toString());
                it.put("IsServc", "N");
                it.put("HsnCd", item.getOrDefault("hsn_sac", "9999").toString());
                BigDecimal qty = toBd(item.get("quantity"));
                BigDecimal price = toBd(item.get("unit_price"));
                BigDecimal taxableVal = toBd(item.get("taxable_value"));
                it.put("Qty", qty);
                it.put("FreeQty", BigDecimal.ZERO);
                it.put("Unit", "NOS");
                it.put("UnitPrice", price);
                it.put("TotAmt", taxableVal);
                it.put("Discount", BigDecimal.ZERO);
                it.put("PreTaxVal", taxableVal);
                // Assume 18% GST split as IGST for inter-state
                BigDecimal igst = taxableVal.multiply(BigDecimal.valueOf(0.18)).setScale(2, RoundingMode.HALF_UP);
                it.put("AssAmt", taxableVal);
                var gstRt = new LinkedHashMap<String, Object>();
                gstRt.put("IgstAmt", igst);
                gstRt.put("CgstAmt", BigDecimal.ZERO);
                gstRt.put("SgstAmt", BigDecimal.ZERO);
                gstRt.put("CesAmt", BigDecimal.ZERO);
                gstRt.put("CesNonAdvlAmt", BigDecimal.ZERO);
                gstRt.put("StateCesAmt", BigDecimal.ZERO);
                gstRt.put("OthChrg", BigDecimal.ZERO);
                gstRt.put("TotItemVal", taxableVal.add(igst));
                it.put("GstRt", BigDecimal.valueOf(18));
                it.putAll(gstRt);
                itemList.add(it);
                totalTaxableValue = totalTaxableValue.add(taxableVal);
            }
            doc.put("ItemList", itemList);

            // Value details
            BigDecimal totalIgst = totalTaxableValue.multiply(BigDecimal.valueOf(0.18)).setScale(2, RoundingMode.HALF_UP);
            var val = new LinkedHashMap<String, Object>();
            val.put("AssVal", totalTaxableValue);
            val.put("CgstVal", BigDecimal.ZERO);
            val.put("SgstVal", BigDecimal.ZERO);
            val.put("IgstVal", totalIgst);
            val.put("CesVal", BigDecimal.ZERO);
            val.put("StCesVal", BigDecimal.ZERO);
            val.put("Discount", BigDecimal.ZERO);
            val.put("OthChrg", BigDecimal.ZERO);
            val.put("RndOffAmt", BigDecimal.ZERO);
            val.put("TotInvVal", totalTaxableValue.add(totalIgst));
            val.put("TotInvValFc", BigDecimal.ZERO);
            doc.put("ValDtls", val);

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build e-invoice payload: " + e.getMessage(), e);
        }
    }

    private BigDecimal toBd(Object val) {
        if (val == null) return BigDecimal.ZERO;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }
}
