package com.mfg.ims.integration.aftership;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * Aftership background polling service (16.4).
 * Every 5 minutes, polls Aftership for updates on all active shipments.
 * Silently skips polling when aftership.api_key is not configured.
 */
@Service
public class AftershipPollingService {

    private static final Logger log = LoggerFactory.getLogger(AftershipPollingService.class);
    private static final String AFTERSHIP_BASE = "https://api.aftership.com/v4";

    private final NamedParameterJdbcTemplate jdbc;
    private final RestTemplate rest = new RestTemplate();

    public AftershipPollingService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "300000")
    @Transactional
    public void pollAll() {
        String apiKey = setting("aftership.api_key");
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("AfterShip polling skipped: aftership.api_key not configured");
            return;
        }

        // Find active shipments — not yet delivered/failed/cancelled
        List<Map<String, Object>> active = jdbc.queryForList("""
                SELECT id, courier_code, tracking_number
                  FROM ims.shipment_trackings
                 WHERE status NOT IN ('DELIVERED','FAILED','CANCELLED','InfoReceived')
                 ORDER BY id
                """, new MapSqlParameterSource());

        if (active.isEmpty()) {
            log.debug("AfterShip polling: no active shipments to poll");
            return;
        }

        log.info("AfterShip polling: checking {} active shipments", active.size());
        Instant now = Instant.now();

        for (Map<String, Object> row : active) {
            Long id = ((Number) row.get("id")).longValue();
            String courierCode = String.valueOf(row.get("courier_code"));
            String trackingNumber = String.valueOf(row.get("tracking_number"));
            try {
                String newStatus = fetchStatus(courierCode, trackingNumber, apiKey);
                if (newStatus != null) {
                    jdbc.update("""
                            UPDATE ims.shipment_trackings
                               SET status = :status, last_updated = :now, last_polled_at = :now
                             WHERE id = :id
                            """,
                            new MapSqlParameterSource()
                                    .addValue("status", newStatus)
                                    .addValue("now", now)
                                    .addValue("id", id));
                    if ("Delivered".equalsIgnoreCase(newStatus)) {
                        log.info("AfterShip: shipment {} ({}/{}) delivered", id, courierCode, trackingNumber);
                    }
                } else {
                    // Just update the polled timestamp even if no status change
                    jdbc.update("""
                            UPDATE ims.shipment_trackings SET last_polled_at = :now WHERE id = :id
                            """,
                            new MapSqlParameterSource().addValue("now", now).addValue("id", id));
                }
            } catch (Exception e) {
                log.debug("AfterShip poll error for tracking {}: {}", trackingNumber, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchStatus(String courierCode, String trackingNumber, String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("aftership-api-key", apiKey);
            var response = rest.exchange(
                    AFTERSHIP_BASE + "/trackings/" + courierCode + "/" + trackingNumber,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            if (response.getBody() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
                if (data != null) {
                    Map<String, Object> tracking = (Map<String, Object>) data.get("tracking");
                    if (tracking != null) {
                        return (String) tracking.get("tag");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("AfterShip GET tracking API error: {}", e.getMessage());
        }
        return null;
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
}
