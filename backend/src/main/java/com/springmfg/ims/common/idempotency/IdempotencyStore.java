package com.springmfg.ims.common.idempotency;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persistence for {@code idempotency_keys}. Every call runs in its own short auto-commit statement (the filter
 * has no transaction), so a claim is visible to concurrent requests at once.
 */
public class IdempotencyStore {

    /** What {@link #claim} found. */
    public enum ClaimState {
        /** This request owns the key and must run. */
        CLAIMED,
        /** The same request already completed: answer with {@link Claim#stored()}. */
        REPLAY,
        /** The key was used for a different request. */
        MISMATCH,
        /** The same request is still running elsewhere. */
        IN_PROGRESS
    }

    /** A completed response as stored. {@code body} is JSON text or null. */
    public record StoredResponse(int status, String body, Map<String, String> headers) {
    }

    public record Claim(ClaimState state, StoredResponse stored) {

        static Claim of(ClaimState state) {
            return new Claim(state, null);
        }
    }

    private static final String TAKE = """
            INSERT INTO idempotency_keys (key, user_id, request_hash, created_at) VALUES (?, ?, ?, now())
            ON CONFLICT (key) DO UPDATE SET request_hash = EXCLUDED.request_hash, response_status = NULL,
                response_body = NULL, response_headers = NULL, created_at = now()
              WHERE idempotency_keys.response_status IS NULL
                AND idempotency_keys.created_at < now() - make_interval(secs => ?)
            RETURNING key""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Duration abandonedAfter;

    /**
     * @param abandonedAfter an unfinished claim older than this is presumed dead (crashed request) and may be taken over
     */
    public IdempotencyStore(JdbcTemplate jdbc, ObjectMapper json, Duration abandonedAfter) {
        this.jdbc = jdbc;
        this.json = json;
        this.abandonedAfter = abandonedAfter;
    }

    public Claim claim(String key, long userId, String requestHash) {
        for (int attempt = 0; attempt < 3; attempt++) {
            List<String> taken = jdbc.queryForList(TAKE, String.class, key, userId, requestHash, abandonedAfter.toSeconds());
            if (!taken.isEmpty()) {
                return Claim.of(ClaimState.CLAIMED);
            }
            List<Claim> existing = jdbc.query("""
                    SELECT request_hash, response_status, response_body::text AS body, response_headers::text AS headers
                    FROM idempotency_keys WHERE key = ?""", (rs, n) -> {
                if (!rs.getString("request_hash").trim().equals(requestHash)) {
                    return Claim.of(ClaimState.MISMATCH);
                }
                Integer status = (Integer) rs.getObject("response_status");
                if (status == null) {
                    return Claim.of(ClaimState.IN_PROGRESS);
                }
                return new Claim(ClaimState.REPLAY, new StoredResponse(status, rs.getString("body"), readHeaders(rs.getString("headers"))));
            }, key);
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
            // the row vanished between the two statements (released by its owner): try to claim it again
        }
        return Claim.of(ClaimState.IN_PROGRESS);
    }

    public void complete(String key, int status, String bodyJson, Map<String, String> headers) {
        jdbc.update("UPDATE idempotency_keys SET response_status = ?, response_body = ?::jsonb, response_headers = ?::jsonb WHERE key = ?",
                status, bodyJson, writeHeaders(headers), key);
    }

    /** Gives the key back so the client can retry with it (the request did not succeed). */
    public void release(String key) {
        jdbc.update("DELETE FROM idempotency_keys WHERE key = ?", key);
    }

    /** Deletes keys older than {@code age}; returns how many. */
    public int purgeOlderThan(Duration age) {
        return jdbc.update("DELETE FROM idempotency_keys WHERE created_at < now() - make_interval(secs => ?)", age.toSeconds());
    }

    private String writeHeaders(Map<String, String> headers) {
        try {
            return headers.isEmpty() ? null : json.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> readHeaders(String text) {
        if (text == null) {
            return Collections.emptyMap();
        }
        try {
            return json.readValue(text, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
