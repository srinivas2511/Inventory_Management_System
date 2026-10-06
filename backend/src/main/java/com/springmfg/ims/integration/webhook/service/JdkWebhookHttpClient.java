package com.springmfg.ims.integration.webhook.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Redirects are never followed: a redirect could point a validated target at an internal address. */
@Component
public class JdkWebhookHttpClient implements WebhookHttpClient {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public Response post(String url, Map<String, String> headers, String body, Duration timeout) throws IOException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);
        try {
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                byte[] head = in.readNBytes(MAX_BODY_CHARS * 4);
                String text = new String(head, StandardCharsets.UTF_8);
                if (text.length() > MAX_BODY_CHARS) {
                    text = text.substring(0, MAX_BODY_CHARS);
                }
                return new Response(response.statusCode(), text);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling the webhook target", e);
        }
    }
}
