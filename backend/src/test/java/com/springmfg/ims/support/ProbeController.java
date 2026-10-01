package com.springmfg.ims.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;

/**
 * Stand-in endpoints for full-context tests (test sources only; never in the application jar).
 * <ul>
 * <li>{@code /api/probe/secret} needs {@code PRODUCT_UPDATE}, the permission {@code PUT /api/products/{id}} will
 * require from task 1.9;</li>
 * <li>{@code /api/probe/idempotent/**} POSTs require an {@code Idempotency-Key} (see {@code ProbeIdempotencyRequirement})
 * and count how often they actually ran per {@code marker}, so tests can prove a replay did not execute again.</li>
 * </ul>
 */
@RestController
public class ProbeController {

    private static final Map<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();

    /** How many times the endpoints actually executed for {@code marker}. */
    public static int calls(String marker) {
        AtomicInteger count = CALLS.get(marker);
        return count == null ? 0 : count.get();
    }

    private static int record(String marker) {
        return CALLS.computeIfAbsent(marker, k -> new AtomicInteger()).incrementAndGet();
    }

    @GetMapping("/api/probe/secret")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    public String secret() {
        return "secret";
    }

    @PostMapping("/api/probe/idempotent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> create(@RequestParam String marker,
            @RequestParam(defaultValue = "0") long delayMs, @RequestBody(required = false) Map<String, Object> body)
            throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        int call = record(marker);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("marker", marker);
        result.put("call", call);
        result.put("echo", body);
        return ResponseEntity.created(URI.create("/api/probe/idempotent/" + marker)).body(result);
    }

    @PostMapping("/api/probe/idempotent/nobody")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> noBody(@RequestParam String marker) {
        record(marker);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/probe/idempotent/fail")
    @PreAuthorize("isAuthenticated()")
    public String fail(@RequestParam String marker) {
        record(marker);
        throw new BusinessRuleException(ErrorCode.INSUFFICIENT_STOCK, "not enough");
    }

    @PostMapping("/api/probe/idempotent/boom")
    @PreAuthorize("isAuthenticated()")
    public String boom(@RequestParam String marker) {
        record(marker);
        throw new IllegalStateException("boom");
    }

    @PostMapping(value = "/api/probe/idempotent/text", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("isAuthenticated()")
    public String text(@RequestParam String marker) {
        record(marker);
        return "plain text";
    }

    /** Idempotency is optional here: it is protected only when the client sends a key. */
    @PostMapping("/api/probe/optional")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> optional(@RequestParam String marker) {
        return Map.of("marker", marker, "call", record(marker));
    }
}
