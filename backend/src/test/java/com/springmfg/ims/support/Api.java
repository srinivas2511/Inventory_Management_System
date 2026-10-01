package com.springmfg.ims.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Small helpers for HTTP-level integration tests. */
public final class Api {

    private static final Pattern REFRESH = Pattern.compile("ims_refresh=([^;]*)");

    private Api() {
    }

    public record Session(String accessToken, String refreshToken, JsonNode body) {
    }

    /** Signs in with a user whose password need not be changed; fails the test otherwise. */
    public static Session login(MockMvc mvc, ObjectMapper json, String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String refresh = null;
        if (setCookie != null) {
            Matcher m = REFRESH.matcher(setCookie);
            refresh = m.find() ? m.group(1) : null;
        }
        return new Session(body.path("accessToken").asText(null), refresh, body);
    }

    public static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    public static MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder request, ObjectMapper json,
            Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }
}
