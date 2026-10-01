package com.springmfg.ims.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.common.idempotency.IdempotencyFilter;
import com.springmfg.ims.common.idempotency.IdempotencyIdentity;
import com.springmfg.ims.common.idempotency.IdempotencyRequirement;
import com.springmfg.ims.common.idempotency.IdempotencyStore;
import com.springmfg.ims.common.idempotency.IdempotencyStore.Claim;
import com.springmfg.ims.common.idempotency.IdempotencyStore.ClaimState;
import com.springmfg.ims.common.idempotency.IdempotencyStore.StoredResponse;

/** Decision logic of the filter with a mocked store; the end-to-end behaviour is in IdempotencyIT. */
class IdempotencyFilterTest {

    private final IdempotencyStore store = mock(IdempotencyStore.class);
    private final ObjectMapper json = new ObjectMapper();
    private Optional<Long> user = Optional.of(7L);
    private final IdempotencyRequirement requirement = () -> new AntPathRequestMatcher("/api/dispatches", "POST");
    private final IdempotencyFilter filter = new IdempotencyFilter(store, () -> user, List.of(requirement), json);

    private MockHttpServletRequest post(String path, String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setServletPath(path);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setContentType("application/json");
        if (key != null) {
            request.addHeader("Idempotency-Key", key);
        }
        return request;
    }

    private static FilterChain ok(int status, String body) {
        return (req, res) -> {
            var response = (jakarta.servlet.http.HttpServletResponse) res;
            response.setStatus(status);
            if (body != null) {
                response.setContentType("application/json");
                response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, FilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void readsAndAnonymousCallsPassStraightThrough() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/x");
        get.addHeader("Idempotency-Key", "abc");
        assertThat(run(get, ok(200, "{}")).getStatus()).isEqualTo(200);

        user = Optional.empty();
        assertThat(run(post("/api/dispatches", null, "{}"), ok(204, null)).getStatus()).isEqualTo(204); // 401 comes later in the chain
        verify(store, never()).claim(anyString(), anyLong(), anyString());
    }

    @Test
    void aRequiredKeyThatIsMissingIsRejectedButOptionalEndpointsDoNotNeedOne() throws Exception {
        MockHttpServletResponse missing = run(post("/api/dispatches", null, "{}"), ok(201, "{}"));
        assertThat(missing.getStatus()).isEqualTo(400);
        assertThat(missing.getContentAsString()).contains("VALIDATION_FAILED").contains("Idempotency-Key");

        assertThat(run(post("/api/other", null, "{}"), ok(201, "{}")).getStatus()).isEqualTo(201);
        verify(store, never()).claim(anyString(), anyLong(), anyString());
    }

    @Test
    void malformedKeysAreRejected() throws Exception {
        for (String bad : new String[] { "has space", "x".repeat(61), "semi;colon", "ünï" }) {
            assertThat(run(post("/api/dispatches", bad, "{}"), ok(201, "{}")).getStatus()).as(bad).isEqualTo(400);
        }
        verify(store, never()).claim(anyString(), anyLong(), anyString());
    }

    @Test
    void theKeyIsScopedToTheUserAndTheSameRequestHasTheSameFingerprint() throws Exception {
        when(store.claim(anyString(), anyLong(), anyString())).thenReturn(new Claim(ClaimState.CLAIMED, null));
        run(post("/api/dispatches", "k1", "{\"a\":1}"), ok(201, "{\"id\":1}"));
        run(post("/api/dispatches", "k1", "{\"a\":1}"), ok(201, "{\"id\":1}"));
        run(post("/api/dispatches", "k1", "{\"a\":2}"), ok(201, "{\"id\":1}"));

        var hashes = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store, org.mockito.Mockito.times(3)).claim(eq("7:k1"), eq(7L), hashes.capture());
        assertThat(hashes.getAllValues().get(0)).isEqualTo(hashes.getAllValues().get(1)).isNotEqualTo(hashes.getAllValues().get(2));
    }

    @Test
    void aSuccessfulJsonResponseIsStoredWithItsLocation() throws Exception {
        when(store.claim(anyString(), anyLong(), anyString())).thenReturn(new Claim(ClaimState.CLAIMED, null));
        FilterChain chain = (req, res) -> {
            var response = (jakarta.servlet.http.HttpServletResponse) res;
            response.setStatus(201);
            response.setHeader("Location", "/api/dispatches/9");
            response.setContentType("application/json");
            response.getOutputStream().write("{\"id\":9}".getBytes(StandardCharsets.UTF_8));
        };
        MockHttpServletResponse response = run(post("/api/dispatches", "k2", "{}"), chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":9}"); // the client still gets the body
        verify(store).complete(eq("7:k2"), eq(201), eq("{\"id\":9}"),
                eq(Map.of("Location", "/api/dispatches/9", "Content-Type", "application/json")));
    }

    @Test
    void failuresAndUnstorableResponsesGiveTheKeyBack() throws Exception {
        when(store.claim(anyString(), anyLong(), anyString())).thenReturn(new Claim(ClaimState.CLAIMED, null));
        run(post("/api/dispatches", "k3", "{}"), ok(422, "{\"code\":\"X\"}"));
        run(post("/api/dispatches", "k4", "{}"), ok(500, null));
        FilterChain text = (req, res) -> {
            var response = (jakarta.servlet.http.HttpServletResponse) res;
            response.setStatus(200);
            response.setContentType("text/plain");
            response.getOutputStream().write("ok".getBytes(StandardCharsets.UTF_8));
        };
        run(post("/api/dispatches", "k5", "{}"), text);
        FilterChain explodes = (req, res) -> {
            throw new IllegalStateException("boom");
        };
        try {
            run(post("/api/dispatches", "k6", "{}"), explodes);
        } catch (IllegalStateException expected) {
            // propagates to the container as before
        }

        for (String key : new String[] { "7:k3", "7:k4", "7:k5", "7:k6" }) {
            verify(store).release(key);
        }
        verify(store, never()).complete(anyString(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void aBodylessSuccessIsStoredWithoutABody() throws Exception {
        when(store.claim(anyString(), anyLong(), anyString())).thenReturn(new Claim(ClaimState.CLAIMED, null));
        run(post("/api/dispatches", "k7", "{}"), ok(204, null));
        verify(store).complete(eq("7:k7"), eq(204), eq(null), eq(Map.of()));
    }

    @Test
    void aCompletedRequestIsReplayedWithoutRunningAgain() throws Exception {
        when(store.claim(anyString(), anyLong(), anyString())).thenReturn(new Claim(ClaimState.REPLAY,
                new StoredResponse(201, "{\"id\":9}", Map.of("Location", "/api/dispatches/9", "Content-Type", "application/json"))));
        boolean[] ran = { false };
        MockHttpServletResponse response = run(post("/api/dispatches", "k8", "{}"), (req, res) -> ran[0] = true);

        assertThat(ran[0]).isFalse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":9}");
        assertThat(response.getHeader("Location")).isEqualTo("/api/dispatches/9");
        assertThat(response.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(response.getContentType()).startsWith("application/json");
    }

    @Test
    void aMismatchAndAnInProgressRequestAreConflicts() throws Exception {
        when(store.claim(anyString(), anyLong(), anyString())).thenReturn(new Claim(ClaimState.MISMATCH, null));
        MockHttpServletResponse mismatch = run(post("/api/dispatches", "k9", "{}"), ok(201, "{}"));
        assertThat(mismatch.getStatus()).isEqualTo(409);
        assertThat(mismatch.getContentAsString()).contains("IDEMPOTENCY_REPLAY_MISMATCH");

        when(store.claim(anyString(), anyLong(), anyString())).thenReturn(new Claim(ClaimState.IN_PROGRESS, null));
        MockHttpServletResponse running = run(post("/api/dispatches", "k9", "{}"), ok(201, "{}"));
        assertThat(running.getStatus()).isEqualTo(409);
        assertThat(running.getContentAsString()).contains("IDEMPOTENCY_IN_PROGRESS");
        assertThat(running.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void theControllerStillSeesTheBodyTheFilterReadToFingerprintIt() throws Exception {
        when(store.claim(anyString(), anyLong(), anyString())).thenReturn(new Claim(ClaimState.CLAIMED, null));
        String[] seen = new String[1];
        run(post("/api/dispatches", "k10", "{\"hello\":\"world\"}"), (req, res) -> {
            seen[0] = new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204);
        });
        assertThat(seen[0]).isEqualTo("{\"hello\":\"world\"}");
    }
}
