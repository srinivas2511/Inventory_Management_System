package com.mfg.ims.mrp.forecast;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mfg.ims.mrp.forecast.ForecastTypes.ForecastPoint;
import com.mfg.ims.mrp.forecast.ForecastTypes.ProductHistory;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.net.WebhookTargetValidator;

/**
 * Posts the history to {@code ims.ai.forecast.url} as JSON and reads
 * {@code {"forecasts":[{"productId":1,"targetDate":"2026-11-01","quantity":120}]}} back. The endpoint is operator
 * configuration, but it goes through the same address guard as webhook targets. Redirects are not followed and the
 * response is capped at 2 MB.
 */
@Component
public class HttpAiForecastClient implements AiForecastClient {

    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final String url;
    private final String apiKey;
    private final WebhookTargetValidator validator;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();

    public HttpAiForecastClient(@Value("${ims.ai.forecast.url:}") String url,
                                @Value("${ims.ai.forecast.api-key:}") String apiKey,
                                WebhookTargetValidator validator, ObjectMapper mapper) {
        this.url = url == null ? "" : url.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.validator = validator;
        this.mapper = mapper;
    }

    @Override
    public boolean isConfigured() {
        return !url.isEmpty();
    }

    @Override
    public String endpointHost() {
        try {
            return isConfigured() ? URI.create(url).getHost() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public List<ForecastPoint> forecast(List<ProductHistory> history, int horizonDays) throws IOException {
        if (!isConfigured()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No AI forecast endpoint is configured");
        }
        validator.validate(url);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("horizonDays", horizonDays);
        body.put("history", history);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (!apiKey.isEmpty()) {
            request.header("Authorization", "Bearer " + apiKey);
        }
        try {
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                byte[] bytes = in.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("The forecast service answered HTTP " + response.statusCode());
                }
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new IOException("The forecast response is too large");
                }
                return parse(mapper.readTree(bytes));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling the forecast service", e);
        }
    }

    private static List<ForecastPoint> parse(JsonNode root) throws IOException {
        JsonNode items = root.path("forecasts");
        if (!items.isArray()) {
            throw new IOException("The forecast response has no 'forecasts' array");
        }
        List<ForecastPoint> points = new ArrayList<>();
        for (JsonNode n : items) {
            try {
                points.add(new ForecastPoint(n.path("productId").asLong(),
                        LocalDate.parse(n.path("targetDate").asText()),
                        new java.math.BigDecimal(n.path("quantity").asText())));
            } catch (RuntimeException e) {
                // an unreadable entry is dropped; the adapter counts what it rejects from what it receives
            }
        }
        return points;
    }
}
