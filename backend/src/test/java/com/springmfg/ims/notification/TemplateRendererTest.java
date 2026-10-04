package com.springmfg.ims.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.springmfg.ims.notification.service.TemplateRenderer;

class TemplateRendererTest {

    @Test
    void substitutesVariablesAndIgnoresPlaceholderWhitespace() {
        Map<String, Object> model = Map.of("poNumber", "PO/26/00007", "supplier", "Acme Wire");
        assertThat(TemplateRenderer.render("PO {{poNumber}} to {{ supplier }}.", model))
                .isEqualTo("PO PO/26/00007 to Acme Wire.");
    }

    @Test
    void resolvesDottedPathsIntoNestedMaps() {
        Map<String, Object> model = Map.of("order", Map.of("number", "SO-9", "customer", Map.of("name", "Zed")));
        assertThat(TemplateRenderer.render("{{order.number}} / {{order.customer.name}}", model))
                .isEqualTo("SO-9 / Zed");
    }

    @Test
    void missingAndNullValuesRenderAsNothing() {
        Map<String, Object> model = new HashMap<>();
        model.put("present", "x");
        model.put("nothing", null);
        assertThat(TemplateRenderer.render("[{{absent}}][{{nothing}}][{{present.deeper}}][{{present}}]", model))
                .isEqualTo("[][][][x]");
    }

    @Test
    void doesNotEvaluateAnythingInsideTheValueOrTheTemplate() {
        Map<String, Object> model = Map.of("message", "{{secret}} ${user.home} $1 \\ ", "secret", "LEAK");
        assertThat(TemplateRenderer.render("{{message}}", model)).isEqualTo("{{secret}} ${user.home} $1 \\ ");
        assertThat(TemplateRenderer.render("#{T(java.lang.Runtime)} {{#section}}x{{/section}}", Map.of()))
                .isEqualTo("#{T(java.lang.Runtime)} {{#section}}x{{/section}}");
    }

    @Test
    void nullTemplateIsEmpty() {
        assertThat(TemplateRenderer.render(null, Map.of())).isEmpty();
    }

    @Test
    void subjectLinesCannotCarryLineBreaks() {
        Map<String, Object> model = Map.of("message", "Hello\r\nBcc: attacker@example.com");
        assertThat(TemplateRenderer.renderLine("Alert: {{message}}", model))
                .isEqualTo("Alert: Hello Bcc: attacker@example.com")
                .doesNotContain("\n").doesNotContain("\r");
    }
}
