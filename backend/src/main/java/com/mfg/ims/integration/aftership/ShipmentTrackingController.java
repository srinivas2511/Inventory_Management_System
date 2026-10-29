package com.mfg.ims.integration.aftership;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.integration.aftership.ShipmentTrackingDtos.RegisterTrackingRequest;
import com.mfg.ims.integration.aftership.ShipmentTrackingDtos.ShipmentTrackingDto;
import com.mfg.ims.integration.aftership.ShipmentTrackingDtos.WebhookPayload;

@RestController
@RequestMapping("/api/integration/shipment-tracking")
public class ShipmentTrackingController {

    private static final Logger log = LoggerFactory.getLogger(ShipmentTrackingController.class);
    private static final String AFTERSHIP_BASE = "https://api.aftership.com/v4";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RestTemplate rest = new RestTemplate();

    public ShipmentTrackingController(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SHIPMENT_TRACK_VIEW')")
    public List<ShipmentTrackingDto> list() {
        return jdbc.query(
                "SELECT * FROM ims.shipment_trackings ORDER BY created_at DESC LIMIT 500",
                new MapSqlParameterSource(),
                this::mapRow);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SHIPMENT_TRACK_VIEW')")
    public ShipmentTrackingDto get(@PathVariable Long id) {
        var rows = jdbc.query(
                "SELECT * FROM ims.shipment_trackings WHERE id = :id",
                new MapSqlParameterSource("id", id),
                this::mapRow);
        if (rows.isEmpty()) throw new NotFoundException("Shipment tracking not found: " + id);
        return rows.get(0);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SHIPMENT_TRACK_MANAGE')")
    public ShipmentTrackingDto register(@RequestBody RegisterTrackingRequest req,
                                         @AuthenticationPrincipal CachedUser user) {
        String apiKey = setting("aftership.api_key");
        boolean enabled = Boolean.parseBoolean(setting("aftership.enabled"));

        String trackingUrl = null;
        if (enabled && apiKey != null && !apiKey.isBlank()) {
            trackingUrl = registerWithAfterShip(req.courierCode(), req.trackingNumber(), apiKey);
        } else {
            trackingUrl = "https://track.aftership.com/" + req.courierCode() + "/" + req.trackingNumber();
            log.info("AfterShip stub: mock URL for {}/{}", req.courierCode(), req.trackingNumber());
        }

        Long id = jdbc.queryForObject("""
                INSERT INTO ims.shipment_trackings
                    (dispatch_id, courier_code, tracking_number, tracking_url, created_by)
                VALUES (:dId, :courier, :trackNo, :url, :uid)
                RETURNING id
                """,
                new MapSqlParameterSource()
                        .addValue("dId", req.dispatchId())
                        .addValue("courier", req.courierCode())
                        .addValue("trackNo", req.trackingNumber())
                        .addValue("url", trackingUrl)
                        .addValue("uid", user.id()),
                Long.class);
        return get(id);
    }

    @PostMapping("/webhook")
    @PreAuthorize("permitAll()")
    public void webhook(@RequestBody Map<String, Object> payload) {
        // AfterShip webhook — update tracking status from inbound push
        try {
            Object msgObj = payload.get("msg");
            if (msgObj == null) return;
            @SuppressWarnings("unchecked") Map<String, Object> msg = (Map<String, Object>) msgObj;
            String trackingNumber = String.valueOf(msg.getOrDefault("tracking_number", ""));
            String tag = String.valueOf(msg.getOrDefault("tag", ""));
            String payloadJson = mapper.writeValueAsString(payload);

            jdbc.update("""
                    UPDATE ims.shipment_trackings
                    SET status = :status, last_updated = :now, raw_payload = :payload::jsonb
                    WHERE tracking_number = :trackNo
                    """,
                    new MapSqlParameterSource()
                            .addValue("status", tag)
                            .addValue("now", Instant.now())
                            .addValue("payload", payloadJson)
                            .addValue("trackNo", trackingNumber));
        } catch (Exception e) {
            log.warn("AfterShip webhook processing error: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private String registerWithAfterShip(String courierCode, String trackingNumber, String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("aftership-api-key", apiKey);

            Map<String, Object> body = Map.of("tracking", Map.of(
                    "tracking_number", trackingNumber,
                    "slug", courierCode));

            var response = rest.postForObject(AFTERSHIP_BASE + "/trackings",
                    new HttpEntity<>(body, headers), Map.class);
            if (response != null) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null) {
                    Map<String, Object> tracking = (Map<String, Object>) data.get("tracking");
                    if (tracking != null) return (String) tracking.get("tracking_url");
                }
            }
        } catch (Exception e) {
            log.warn("AfterShip API error: {}", e.getMessage());
        }
        return "https://track.aftership.com/" + courierCode + "/" + trackingNumber;
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

    private ShipmentTrackingDto mapRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new ShipmentTrackingDto(
                rs.getLong("id"),
                rs.getLong("dispatch_id") == 0 ? null : rs.getLong("dispatch_id"),
                rs.getString("courier_code"),
                rs.getString("tracking_number"),
                rs.getString("tracking_url"),
                rs.getString("status"),
                rs.getString("last_checkpoint"),
                rs.getTimestamp("last_updated") != null ? rs.getTimestamp("last_updated").toInstant() : null,
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : null
        );
    }
}
