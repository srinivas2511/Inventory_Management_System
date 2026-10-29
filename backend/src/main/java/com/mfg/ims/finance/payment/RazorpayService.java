package com.mfg.ims.finance.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

/**
 * Razorpay payment gateway stub (V79).
 * When razorpay.enabled=false or key_id is blank, returns a deterministic mock order
 * so the frontend can be exercised in sandbox mode without live credentials.
 * When enabled, POSTs to https://api.razorpay.com/v1/orders and returns the real order id.
 */
@Service
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);
    private static final String RAZORPAY_ORDERS_URL = "https://api.razorpay.com/v1/orders";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RestTemplate rest = new RestTemplate();

    public RazorpayService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public record CreateOrderRequest(Long arInvoiceId, String currency) {}

    public record OrderResponse(
            Long dbId,
            String razorpayOrderId,
            Long amountPaise,
            String currency,
            String keyId,
            boolean liveMode
    ) {}

    public record VerifyRequest(
            Long dbId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) {}

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req, Long userId) {
        BigDecimal amount = fetchInvoiceBalance(req.arInvoiceId());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Invoice has no outstanding balance");
        }

        String currency = req.currency() != null ? req.currency() : "INR";
        long amountPaise = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();

        String keyId = setting("payment.razorpay.key_id");
        String keySecret = setting("payment.razorpay.key_secret");
        boolean enabled = Boolean.parseBoolean(setting("payment.razorpay.enabled"));

        String razorpayOrderId;
        if (enabled && keyId != null && !keyId.isBlank()) {
            razorpayOrderId = callRazorpayApi(amountPaise, currency, keyId, keySecret);
        } else {
            // Stub mode: generate a deterministic mock order id
            razorpayOrderId = "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            log.info("Razorpay stub: mock order {} for invoice {}", razorpayOrderId, req.arInvoiceId());
        }

        // Persist the order record
        Long dbId = jdbc.queryForObject("""
                INSERT INTO ims.razorpay_orders
                    (ar_invoice_id, razorpay_order_id, amount_paise, currency, created_by)
                VALUES (:invId, :orderId, :amount, :currency, :userId)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("invId", req.arInvoiceId())
                        .addValue("orderId", razorpayOrderId)
                        .addValue("amount", amountPaise)
                        .addValue("currency", currency)
                        .addValue("userId", userId),
                Long.class);

        return new OrderResponse(dbId, razorpayOrderId, amountPaise, currency, keyId, enabled);
    }

    @Transactional
    public boolean verifyAndCapture(VerifyRequest req) {
        var rows = jdbc.queryForList(
                "SELECT * FROM ims.razorpay_orders WHERE id = :id",
                new MapSqlParameterSource("id", req.dbId()));
        if (rows.isEmpty()) throw new NotFoundException("Razorpay order not found: " + req.dbId());
        var row = rows.get(0);

        String keySecret = setting("payment.razorpay.key_secret");
        boolean live = Boolean.parseBoolean(setting("payment.razorpay.enabled"));

        boolean valid;
        if (live && keySecret != null && !keySecret.isBlank()) {
            // Verify HMAC-SHA256 signature: orderId|paymentId signed with keySecret
            String payload = req.razorpayOrderId() + "|" + req.razorpayPaymentId();
            valid = hmacMatches(payload, keySecret, req.razorpaySignature());
        } else {
            // Stub mode: accept any non-blank signature
            valid = req.razorpaySignature() != null && !req.razorpaySignature().isBlank();
        }

        if (valid) {
            jdbc.update("""
                    UPDATE ims.razorpay_orders
                    SET status = 'CAPTURED', razorpay_payment_id = :payId,
                        razorpay_signature = :sig, verified_at = :now
                    WHERE id = :id
                    """,
                    new MapSqlParameterSource()
                            .addValue("payId", req.razorpayPaymentId())
                            .addValue("sig", req.razorpaySignature())
                            .addValue("now", Instant.now())
                            .addValue("id", req.dbId()));
        }
        return valid;
    }

    // ── private helpers ─────────────────────────────────────────────────────────

    private BigDecimal fetchInvoiceBalance(Long invoiceId) {
        if (invoiceId == null) throw new NotFoundException("AR invoice not found: null");
        try {
            BigDecimal balance = jdbc.queryForObject(
                    "SELECT balance_amount FROM ims.ar_invoices WHERE id = :id",
                    new MapSqlParameterSource("id", invoiceId), BigDecimal.class);
            return balance != null ? balance : BigDecimal.ZERO;
        } catch (Exception e) {
            throw new NotFoundException("AR invoice not found: " + invoiceId);
        }
    }

    @SuppressWarnings("unchecked")
    private String callRazorpayApi(long amountPaise, String currency, String keyId, String keySecret) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + auth);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("amount", amountPaise);
            body.put("currency", currency);
            body.put("receipt", "rcpt_" + System.currentTimeMillis());

            var response = rest.postForObject(RAZORPAY_ORDERS_URL,
                    new HttpEntity<>(mapper.writeValueAsString(body), headers), Map.class);
            if (response == null) throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Razorpay returned empty response");
            return (String) response.get("id");
        } catch (RestClientException e) {
            throw new BusinessRuleException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Razorpay API error: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Razorpay: " + e.getMessage(), e);
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

    private static boolean hmacMatches(String payload, String secret, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = bytesToHex(hash);
            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
