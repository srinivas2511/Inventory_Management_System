package com.springmfg.ims.common.idempotency;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.Problems;

/**
 * Makes retried and double-tapped writes safe (DESIGN.md sections 6.1 and 7.3, ARCHITECTURE.md section 6).
 * <p>
 * A signed-in client sends {@code Idempotency-Key: <uuid>} on a POST/PUT/PATCH/DELETE. The first request with a key
 * runs normally and, if it <b>succeeds (2xx)</b>, its response is stored. A repeat of the <em>same</em> request
 * (same method, path, query and body) gets the stored response back with {@code Idempotent-Replayed: true} and does
 * not run again. The same key with a different request is {@code 409 IDEMPOTENCY_REPLAY_MISMATCH}; the same request
 * while the first is still running is {@code 409 IDEMPOTENCY_IN_PROGRESS}. A request that does not succeed gives the
 * key back, so the client may retry with it.
 * <p>
 * Properties worth knowing:
 * <ul>
 * <li>keys are scoped to the user ({@code userId:key}); nobody can read or block another user's key;</li>
 * <li>the filter only acts for authenticated users (it sits after the JWT filter) and cannot see who may do what, so
 * a replay returns the caller's <em>own earlier</em> successful response even if they have since lost the permission.
 * It never re-executes anything;</li>
 * <li>endpoints registered as {@link IdempotencyRequirement} reject a missing key with 400;</li>
 * <li>JSON bodies up to 1 MiB; a non-JSON success body cannot be stored, so such a response is not protected.</li>
 * </ul>
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    static final int MAX_BODY_BYTES = 1_048_576;
    static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9_-]{1,60}");
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyStore store;
    private final IdempotencyIdentity identity;
    private final List<IdempotencyRequirement> requirements;
    private final ObjectMapper json;

    public IdempotencyFilter(IdempotencyStore store, IdempotencyIdentity identity,
            List<IdempotencyRequirement> requirements, ObjectMapper json) {
        this.store = store;
        this.identity = identity;
        this.requirements = List.copyOf(requirements);
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WRITE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Long> user = identity.currentUserId();
        if (user.isEmpty()) {
            chain.doFilter(request, response); // not signed in: the 401 comes from the security chain
            return;
        }
        String header = request.getHeader(HEADER);
        if (header == null) {
            if (requirements.stream().anyMatch(r -> r.matcher().matches(request))) {
                problem(response, ErrorCode.VALIDATION_FAILED, "This operation needs an Idempotency-Key header (a UUID).", null);
            } else {
                chain.doFilter(request, response);
            }
            return;
        }
        if (!KEY_FORMAT.matcher(header).matches()) {
            problem(response, ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be 1-60 letters, digits, '-' or '_' (a UUID is ideal).", null);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            problem(response, ErrorCode.VALIDATION_FAILED, "The request body is too large for an idempotent request.", null);
            return;
        }
        HttpServletRequest replayable = new CachedBodyRequest(request, body);
        String storedKey = user.get() + ":" + header;

        IdempotencyStore.Claim claim = store.claim(storedKey, user.get(), fingerprint(request, body));
        switch (claim.state()) {
            case CLAIMED -> execute(replayable, response, chain, storedKey);
            case REPLAY -> replay(response, claim.stored());
            case MISMATCH -> problem(response, ErrorCode.IDEMPOTENCY_REPLAY_MISMATCH,
                    "This Idempotency-Key was already used for a different request.", null);
            case IN_PROGRESS -> problem(response, ErrorCode.IDEMPOTENCY_IN_PROGRESS,
                    "A request with this Idempotency-Key is still being processed. Retry shortly.", "1");
        }
    }

    private void execute(HttpServletRequest request, HttpServletResponse response, FilterChain chain, String storedKey)
            throws IOException, ServletException {
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean finished = false;
        try {
            chain.doFilter(request, wrapped);
            finished = true;
        } finally {
            try {
                if (finished) {
                    remember(storedKey, wrapped);
                } else {
                    store.release(storedKey); // the request blew up: let the client retry
                }
            } finally {
                wrapped.copyBodyToResponse();
            }
        }
    }

    private void remember(String storedKey, ContentCachingResponseWrapper response) {
        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            store.release(storedKey); // only success consumes a key
            return;
        }
        byte[] content = response.getContentAsByteArray();
        String contentType = response.getContentType();
        String bodyJson = null;
        if (content.length > 0) {
            if (contentType == null || !contentType.toLowerCase().contains("json")) {
                store.release(storedKey); // cannot be replayed faithfully
                return;
            }
            bodyJson = new String(content, StandardCharsets.UTF_8);
            try {
                json.readTree(bodyJson);
            } catch (JsonProcessingException e) {
                store.release(storedKey);
                return;
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        if (response.getHeader(HttpHeaders.LOCATION) != null) {
            headers.put(HttpHeaders.LOCATION, response.getHeader(HttpHeaders.LOCATION));
        }
        if (contentType != null) {
            headers.put(HttpHeaders.CONTENT_TYPE, contentType);
        }
        store.complete(storedKey, status, bodyJson, headers);
    }

    private void replay(HttpServletResponse response, IdempotencyStore.StoredResponse stored) throws IOException {
        response.setStatus(stored.status());
        stored.headers().forEach((name, value) -> {
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                response.setContentType(value);
            } else {
                response.setHeader(name, value);
            }
        });
        response.setHeader(REPLAYED_HEADER, "true");
        if (stored.body() != null) {
            response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void problem(HttpServletResponse response, ErrorCode code, String detail, String retryAfter) throws IOException {
        ProblemDetail problem = Problems.of(code, detail, code == ErrorCode.VALIDATION_FAILED
                ? List.of(new Problems.FieldError(HEADER, detail)) : List.of());
        response.setStatus(problem.getStatus());
        if (retryAfter != null) {
            response.setHeader(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }

    /** SHA-256 over method, path, query and body: "the same request". */
    static String fingerprint(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((request.getMethod() + "\n" + request.getRequestURI() + "\n"
                    + (request.getQueryString() == null ? "" : request.getQueryString()) + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lets the controller read the body that the filter already consumed to fingerprint it. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
