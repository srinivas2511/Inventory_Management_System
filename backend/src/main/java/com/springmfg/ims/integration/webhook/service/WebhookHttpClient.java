package com.springmfg.ims.integration.webhook.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/** The one outbound HTTP call a webhook delivery makes; replaced by a test double in unit tests. */
public interface WebhookHttpClient {

    /** Largest response body kept, in characters. */
    int MAX_BODY_CHARS = 2000;

    Response post(String url, Map<String, String> headers, String body, Duration timeout) throws IOException;

    record Response(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }
}
