package com.springmfg.ims.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.common.idempotency.IdempotencyPurgeJob;
import com.springmfg.ims.iam.User;
import com.springmfg.ims.support.AbstractIntegrationTest;
import com.springmfg.ims.support.Api;
import com.springmfg.ims.support.ProbeController;
import com.springmfg.ims.support.TestUsers;

/**
 * Task 1.7: the Idempotency-Key filter through the real security chain, with endpoints that count how often they
 * really ran. DESIGN.md INV-08: replaying a POST with the same key creates one effect and returns the same response.
 */
class IdempotencyIT extends AbstractIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    TestUsers testUsers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdempotencyPurgeJob purge;

    private record Caller(User user, String token) {
    }

    private Caller caller() throws Exception {
        User user = testUsers.create("SALES");
        return new Caller(user, Api.login(mockMvc, json, user.getUsername(), TestUsers.PASSWORD).accessToken());
    }

    private static String marker() {
        return "idem" + SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResultActions send(Caller caller, String path, String key, Object body) throws Exception {
        MockHttpServletRequestBuilder request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(json.writeValueAsString(body));
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        if (caller != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + caller.token());
        }
        return mockMvc.perform(request);
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    // ------------------------------------------------------------------------------------------- replay

    @Test
    void repeatingTheSameRequestReturnsTheSameResponseAndRunsOnce() throws Exception {
        Caller caller = caller();
        String m = marker();
        String key = key();
        Map<String, Object> body = Map.of("qty", 5);

        MvcResult first = send(caller, "/api/probe/idempotent?marker=" + m, key, body).andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed")).andExpect(jsonPath("$.call").value(1)).andReturn();
        MvcResult second = send(caller, "/api/probe/idempotent?marker=" + m, key, body).andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true")).andExpect(jsonPath("$.call").value(1))
                .andExpect(header().string("Location", "/api/probe/idempotent/" + m)).andReturn();
        send(caller, "/api/probe/idempotent?marker=" + m, key, body).andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));

        assertThat(ProbeController.calls(m)).as("the endpoint ran exactly once").isEqualTo(1);
        assertThat(json.readTree(second.getResponse().getContentAsString())).isEqualTo(json.readTree(first.getResponse().getContentAsString()));
        assertThat(second.getResponse().getContentType()).startsWith("application/json");
    }

    @Test
    void aBodylessSuccessIsReplayedAsABodylessSuccess() throws Exception {
        Caller caller = caller();
        String m = marker();
        String key = key();
        send(caller, "/api/probe/idempotent/nobody?marker=" + m, key, null).andExpect(status().isNoContent());
        MvcResult replay = send(caller, "/api/probe/idempotent/nobody?marker=" + m, key, null).andExpect(status().isNoContent())
                .andExpect(header().string("Idempotent-Replayed", "true")).andReturn();
        assertThat(replay.getResponse().getContentAsString()).isEmpty();
        assertThat(ProbeController.calls(m)).isEqualTo(1);
    }

    @Test
    void theStoredRowHoldsTheUserScopedKeyStatusBodyAndLocation() throws Exception {
        Caller caller = caller();
        String m = marker();
        String key = key();
        send(caller, "/api/probe/idempotent?marker=" + m, key, Map.of("a", 1)).andExpect(status().isCreated());

        Map<String, Object> row = jdbc.queryForMap("""
                SELECT key, user_id, response_status, response_body ->> 'marker' AS marker,
                       response_headers ->> 'Location' AS location FROM idempotency_keys WHERE key = ?""", caller.user().getId() + ":" + key);
        assertThat(row.get("user_id")).isEqualTo(caller.user().getId());
        assertThat(row.get("response_status")).isEqualTo(201);
        assertThat(row.get("marker")).isEqualTo(m);
        assertThat(row.get("location")).isEqualTo("/api/probe/idempotent/" + m);
    }

    // ------------------------------------------------------------------------------------------- mismatch

    @Test
    void theSameKeyWithADifferentRequestIsAConflictAndDoesNotRun() throws Exception {
        Caller caller = caller();
        String m = marker();
        String key = key();
        send(caller, "/api/probe/idempotent?marker=" + m, key, Map.of("qty", 5)).andExpect(status().isCreated());

        send(caller, "/api/probe/idempotent?marker=" + m, key, Map.of("qty", 6)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_REPLAY_MISMATCH"));
        send(caller, "/api/probe/idempotent?marker=" + m + "&delayMs=0", key, Map.of("qty", 5)).andExpect(status().isConflict()); // other query
        send(caller, "/api/probe/idempotent/nobody?marker=" + m, key, null).andExpect(status().isConflict()); // other path
        assertThat(ProbeController.calls(m)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------- key rules

    @Test
    void anEndpointThatRequiresAKeyRejectsAMissingOrMalformedOne() throws Exception {
        Caller caller = caller();
        String m = marker();
        send(caller, "/api/probe/idempotent?marker=" + m, null, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.fieldErrors[0].field").value("Idempotency-Key"));
        send(caller, "/api/probe/idempotent?marker=" + m, "not a valid key!", Map.of()).andExpect(status().isBadRequest());
        send(caller, "/api/probe/idempotent?marker=" + m, "k".repeat(61), Map.of()).andExpect(status().isBadRequest());
        assertThat(ProbeController.calls(m)).isZero();
    }

    @Test
    void anOptionalEndpointRunsEveryTimeWithoutAKeyAndOnceWithOne() throws Exception {
        Caller caller = caller();
        String m = marker();
        send(caller, "/api/probe/optional?marker=" + m, null, null).andExpect(status().isOk());
        send(caller, "/api/probe/optional?marker=" + m, null, null).andExpect(status().isOk());
        assertThat(ProbeController.calls(m)).isEqualTo(2);

        String key = key();
        send(caller, "/api/probe/optional?marker=" + m, key, null).andExpect(jsonPath("$.call").value(3));
        send(caller, "/api/probe/optional?marker=" + m, key, null).andExpect(jsonPath("$.call").value(3))
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(ProbeController.calls(m)).isEqualTo(3);
    }

    @Test
    void keysAreScopedToTheUser() throws Exception {
        Caller alice = caller();
        Caller bob = caller();
        String m = marker();
        String shared = key();

        send(alice, "/api/probe/idempotent?marker=" + m, shared, Map.of("who", "alice")).andExpect(status().isCreated()).andExpect(jsonPath("$.call").value(1));
        // Bob using Alice's key neither sees her response nor is blocked by it
        send(bob, "/api/probe/idempotent?marker=" + m, shared, Map.of("who", "bob")).andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed")).andExpect(jsonPath("$.call").value(2));
        assertThat(ProbeController.calls(m)).isEqualTo(2);
    }

    @Test
    void anonymousCallsAreRefusedByTheSecurityChainAndStoreNothing() throws Exception {
        String key = key();
        send(null, "/api/probe/idempotent?marker=" + marker(), key, Map.of()).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE key LIKE ?", Integer.class, "%" + key)).isZero();
    }

    // ------------------------------------------------------------------------------------------- failures

    @Test
    void aFailedRequestGivesTheKeyBackSoTheClientCanRetry() throws Exception {
        Caller caller = caller();
        String m = marker();
        String key = key();

        send(caller, "/api/probe/idempotent/fail?marker=" + m, key, null).andExpect(status().isUnprocessableEntity());
        send(caller, "/api/probe/idempotent/fail?marker=" + m, key, null).andExpect(status().isUnprocessableEntity())
                .andExpect(header().doesNotExist("Idempotent-Replayed")).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(ProbeController.calls(m)).as("a failure does not consume the key").isEqualTo(2);

        String boomKey = key();
        send(caller, "/api/probe/idempotent/boom?marker=" + m, boomKey, null).andExpect(status().isInternalServerError());
        send(caller, "/api/probe/idempotent/boom?marker=" + m, boomKey, null).andExpect(status().isInternalServerError());
        assertThat(ProbeController.calls(m)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE key IN (?, ?, ?)", Integer.class,
                caller.user().getId() + ":" + key, caller.user().getId() + ":" + boomKey, "x")).isZero();
    }

    @Test
    void aResponseThatCannotBeStoredAsJsonIsNotProtected() throws Exception {
        Caller caller = caller();
        String m = marker();
        String key = key();
        send(caller, "/api/probe/idempotent/text?marker=" + m, key, null).andExpect(status().isOk());
        send(caller, "/api/probe/idempotent/text?marker=" + m, key, null).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replayed"));
        assertThat(ProbeController.calls(m)).isEqualTo(2);
    }

    // ------------------------------------------------------------------------------------------- concurrency and recovery

    @Test
    void twoSimultaneousIdenticalRequestsRunTheEndpointOnce() throws Exception {
        Caller caller = caller();
        String m = marker();
        String key = key();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return send(caller, "/api/probe/idempotent?marker=" + m + "&delayMs=600", key, Map.of("qty", 1))
                            .andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(ProbeController.calls(m)).as("the endpoint ran once").isEqualTo(1);
            assertThat(statuses).containsExactlyInAnyOrder(201, 409); // the loser is told it is still in progress
        } finally {
            pool.shutdownNow();
        }
        // afterwards the key replays normally
        send(caller, "/api/probe/idempotent?marker=" + m + "&delayMs=600", key, Map.of("qty", 1)).andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
        assertThat(ProbeController.calls(m)).isEqualTo(1);
    }

    @Test
    void anAbandonedClaimFromACrashedRequestIsTakenOverButAFreshOneIsRespected() throws Exception {
        Caller caller = caller();
        String m = marker();

        String stale = key();
        jdbc.update("INSERT INTO idempotency_keys (key, user_id, request_hash, created_at) VALUES (?, ?, ?, ?)",
                caller.user().getId() + ":" + stale, caller.user().getId(), "0".repeat(64), Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES)));
        send(caller, "/api/probe/idempotent?marker=" + m, stale, Map.of()).andExpect(status().isCreated());
        assertThat(ProbeController.calls(m)).isEqualTo(1);

        String fresh = key();
        jdbc.update("INSERT INTO idempotency_keys (key, user_id, request_hash, created_at) VALUES (?, ?, ?, now())",
                caller.user().getId() + ":" + fresh, caller.user().getId(), "0".repeat(64));
        send(caller, "/api/probe/idempotent?marker=" + m, fresh, Map.of()).andExpect(status().isConflict()); // someone else's run, not ours
        assertThat(ProbeController.calls(m)).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------- purge

    @Test
    void thePurgeJobRemovesKeysOlderThanFortyEightHoursOnly() {
        long userId = testUsers.create("SALES").getId();
        String old = "purge-old-" + UUID.randomUUID();
        String recent = "purge-recent-" + UUID.randomUUID();
        jdbc.update("INSERT INTO idempotency_keys (key, user_id, request_hash, created_at) VALUES (?, ?, ?, ?)", old, userId, "0".repeat(64),
                Timestamp.from(Instant.now().minus(49, ChronoUnit.HOURS)));
        jdbc.update("INSERT INTO idempotency_keys (key, user_id, request_hash, created_at) VALUES (?, ?, ?, ?)", recent, userId, "0".repeat(64),
                Timestamp.from(Instant.now().minus(47, ChronoUnit.HOURS)));

        purge.purge();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE key = ?", Integer.class, old)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE key = ?", Integer.class, recent)).isEqualTo(1);
    }
}
