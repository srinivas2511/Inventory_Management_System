package com.mfg.ims.notification.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small Mustache subset: {@code {{name}}} and dotted paths {@code {{order.number}}} into nested maps; a missing or
 * null value renders as nothing. There are no sections or partials, and nothing is evaluated, so a template can
 * only ever read the payload it is given. Output is plain text (e-mail body, in-app message): the Angular client
 * escapes it on display.
 */
public final class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

    private TemplateRenderer() {
    }

    public static String render(String template, Map<String, Object> model) {
        if (template == null) {
            return "";
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(lookup(model, m.group(1))));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** For a header such as an e-mail subject: rendered, with any line breaks collapsed (no header injection). */
    public static String renderLine(String template, Map<String, Object> model) {
        return render(template, model).replaceAll("[\\r\\n]+", " ").trim();
    }

    @SuppressWarnings("unchecked")
    private static String lookup(Map<String, Object> model, String path) {
        Object current = model;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return "";
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return "";
            }
        }
        return String.valueOf(current);
    }
}
