package com.springmfg.ims.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.springmfg.ims.support.AbstractIntegrationTest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Holds the Angular sources against the real backend contract. The frontend specs mock {@code HttpClient}, so a screen
 * calling a URL, verb, field or enum value the backend does not have passes every frontend test and fails in the
 * browser. This scans the frontend sources and compares each call with the Spring handler mappings and DTO records:
 * the URL and verb must exist, the response generic must have the shape the backend returns and may only read fields
 * the DTO carries, request bodies must carry the DTO's required fields, and string-literal unions named like a backend
 * enum may only use its constants.
 *
 * <p>Not covered: the type of a body or response that the scan cannot resolve (shown as unresolved in the report),
 * query-parameter names, and whether values have the right format. Known, justified exceptions live in
 * {@code src/test/resources/contract/api-contract-allowlist.txt}; the full report is written to
 * {@code target/api-contract-report.txt}.
 */
class ApiContractIT extends AbstractIntegrationTest {

    private static final Path FRONTEND = Path.of("..", "frontend");
    private static final Path ALLOWLIST = Path.of("src", "test", "resources", "contract", "api-contract-allowlist.txt");
    private static final Path REPORT = Path.of("target", "api-contract-report.txt");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    /** What Jackson writes for org.springframework.data.domain.Page (PageImpl), which most list endpoints return. */
    private static final Set<String> SPRING_PAGE_JSON = Set.of("content", "pageable", "last", "totalPages",
            "totalElements", "size", "number", "sort", "first", "numberOfElements", "empty");

    private int paramChecked;

    record Endpoint(Set<String> verbs, String pattern, List<String> segs, Method method) {}

    enum Kind { NONE, OBJECT, ARRAY, PAGE }

    record JavaShape(Kind kind, Class<?> element, Set<String> wrapperFields) {}

    record TsType(Kind kind, String name, String wrapper) {}

    record Violation(String key, String detail) {}

    @Test
    @DisplayName("Every frontend API call matches a backend endpoint, response shape, request body and enum")
    void frontendCallsMatchTheBackendContract() throws IOException {
        String configBase = readConfigBase();
        String codeDefault = readCodeDefaultBase();
        FrontendSources fs = FrontendSources.scan(FRONTEND.resolve("src").resolve("app"), configBase);
        List<Endpoint> endpoints = endpoints();
        assertThat(fs.calls.size()).as("the scan should find the frontend's HTTP calls").isGreaterThan(150);
        assertThat(endpoints.size()).as("the handler mappings should expose the API").isGreaterThan(300);

        List<Violation> violations = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Map<String, Class<?>> enums = backendEnums();

        if (!configBase.equals(codeDefault)) {
            violations.add(new Violation("BASE|config.json=" + configBase + "|code default=" + codeDefault,
                    "public/config.json and the AppConfigService default must agree on apiBaseUrl"));
        }

        int unresolved = 0;
        int matched = 0;
        int weak = 0;
        Set<String> checkedPairs = new LinkedHashSet<>();
        Map<String, Long> methodCalls = new java.util.HashMap<>();
        fs.calls.forEach(k -> methodCalls.merge(k.methodKey(), 1L, Long::sum));
        for (FrontendSources.Call c : fs.calls) {
            if (c.file().equals("core/api/api.service.ts")) {
                continue;
            }
            if (!c.resolved() || c.url() == null || c.dynamicSuffix()) {
                unresolved++;
                notes.add("UNRESOLVED " + c.verb() + " " + (c.url() != null ? c.url() : c.rawUrl())
                        + (c.dynamicSuffix() ? " (dynamic suffix)" : "") + " @" + c.where());
                if (c.url() == null || !c.resolved()) {
                    continue;
                }
            }
            List<String> segs = FrontendSources.splitTop(c.url(), '/').stream().filter(s -> !s.isEmpty()).toList();
            Endpoint best = null;
            int bestScore = -1;
            boolean bestWeak = false;
            for (Endpoint e : endpoints) {
                int s = score(e, c.verb(), segs);
                if (s > bestScore) {
                    bestScore = s;
                    best = e;
                    bestWeak = isWeak(e, segs);
                }
            }
            if (best == null) {
                violations.add(new Violation("NO_ENDPOINT|" + c.verb() + " " + c.url(),
                        c.verb() + " " + c.url() + " matches no backend endpoint @" + c.where()));
                continue;
            }
            matched++;
            if (bestWeak) {
                weak++;
                notes.add("WEAK " + c.verb() + " " + c.url() + " ~ " + best.pattern() + " @" + c.where());
            }
            String pair = c.verb() + " " + c.url() + " -> " + best.pattern();
            checkResponse(fs, c, best, violations, checkedPairs.add("R|" + pair + "|" + c.generic()));
            // A weak match means a variable segment (e.g. /{id}/{action}) fits several endpoints and "best" is merely the
            // first of them in an unordered list, so its body must not be held against the call.
            if (!bestWeak) {
                checkBody(fs, c, best, violations);
            }
            if (methodCalls.get(c.methodKey()) == 1) {
                checkParams(c, best, violations);
            }
        }

        checkEnums(fs, enums, violations);

        notes.add("PARAM-CHECKED calls: " + paramChecked);
        assertThat(paramChecked).as("the query-parameter check should examine the calls that build parameters")
                .isGreaterThan(15);

        Map<String, String> allowed = readAllowlist();
        List<Violation> unexpected = violations.stream().filter(v -> !allowed.containsKey(v.key())).toList();
        Set<String> used = new TreeSet<>();
        violations.forEach(v -> used.add(v.key()));
        List<String> stale = allowed.keySet().stream().filter(k -> !used.contains(k)).toList();

        writeReport(fs, endpoints.size(), matched, weak, unresolved, violations, allowed, notes, stale);

        assertThat(unexpected.stream().map(v -> v.key() + "  ::  " + v.detail()).toList())
                .as("frontend/backend contract violations (see target/api-contract-report.txt; justified exceptions go in %s)",
                        ALLOWLIST)
                .isEmpty();
        assertThat(stale).as("allowlist entries that no longer match anything must be removed").isEmpty();
    }

    // ------------------------------------------------------------------ backend inventory

    private List<Endpoint> endpoints() {
        List<Endpoint> out = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> en : handlerMapping
                .getHandlerMethods().entrySet()) {
            RequestMappingInfo info = en.getKey();
            Set<String> patterns = info.getPathPatternsCondition() != null
                    ? info.getPathPatternsCondition().getPatternValues()
                    : info.getPatternsCondition().getPatterns();
            Set<String> verbs = new LinkedHashSet<>();
            info.getMethodsCondition().getMethods().forEach(m -> verbs.add(m.name()));
            for (String p : patterns) {
                if (p.startsWith("/api") && !p.contains("*")) {
                    out.add(new Endpoint(verbs, p, segs(p), en.getValue().getMethod()));
                }
            }
        }
        return out;
    }

    private static List<String> segs(String path) {
        List<String> s = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                s.add(part.matches("\\{[^}]*\\}") ? "{}" : part);
            }
        }
        return s;
    }

    private static int score(Endpoint e, String verb, List<String> segs) {
        if (!(e.verbs().isEmpty() || e.verbs().contains(verb)) || e.segs().size() != segs.size()) {
            return -1;
        }
        int score = 0;
        for (int i = 0; i < segs.size(); i++) {
            String b = e.segs().get(i);
            String f = segs.get(i);
            if (b.equals(f)) {
                score += b.equals("{}") ? 1 : 3;
            } else if (b.equals("{}")) {
                score += 1;
            } else if (!f.equals("{}")) {
                return -1;
            }
        }
        return score;
    }

    private static boolean isWeak(Endpoint e, List<String> segs) {
        for (int i = 0; i < segs.size(); i++) {
            if (segs.get(i).equals("{}") && !e.segs().get(i).equals("{}")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Class<?>> backendEnums() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Enum.class));
        Map<String, Class<?>> out = new TreeMap<>();
        Set<String> dup = new TreeSet<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.springmfg.ims")) {
            try {
                Class<?> k = Class.forName(bd.getBeanClassName());
                if (k.isEnum() && out.put(k.getSimpleName(), k) != null) {
                    dup.add(k.getSimpleName());
                }
            } catch (ClassNotFoundException ignored) {
                // not loadable: not an API enum
            }
        }
        dup.forEach(out::remove);
        return out;
    }

    // ------------------------------------------------------------------ response and body checks

    private void checkResponse(FrontendSources fs, FrontendSources.Call c, Endpoint e, List<Violation> violations,
            boolean first) {
        if (!first || c.generic() == null) {
            return;
        }
        JavaShape js = shape(e.method().getGenericReturnType());
        TsType ts = tsType(c.generic());
        String id = c.verb() + " " + c.url();
        if (ts.kind() == Kind.OBJECT && !tsKnown(fs, ts, c)) {
            return; // primitive, Blob, inline type or unknown name: nothing to compare
        }
        if (js.kind() == Kind.NONE || ts == null) {
            return;
        }
        if (ts.kind() != js.kind()) {
            violations.add(new Violation("SHAPE|" + id + "|ts=" + ts.kind() + " java=" + js.kind(),
                    id + " expects " + ts.kind() + " (" + c.generic() + ") but the backend returns " + js.kind()
                            + " (" + e.method().getReturnType().getSimpleName() + ") @" + c.where()));
            return;
        }
        if (ts.kind() == Kind.PAGE && ts.wrapper() != null) {
            FrontendSources.TsInterface w = fs.resolve(ts.wrapper(), c.file());
            if (w != null && js.wrapperFields() != null) {
                for (String f : fs.allFields(w, new LinkedHashSet<>()).keySet()) {
                    if (!js.wrapperFields().contains(f)) {
                        violations.add(new Violation("PAGE_FIELD|" + id + "|" + ts.wrapper() + "." + f,
                                id + ": frontend " + ts.wrapper() + "." + f + " is not in the page the backend returns "
                                        + js.wrapperFields() + " @" + c.where()));
                    }
                }
            }
        }
        FrontendSources.TsInterface t = fs.resolve(ts.name(), c.file());
        if (t == null || skipFields(js.element())) {
            return;
        }
        Set<String> javaFields = javaFields(js.element());
        for (Map.Entry<String, Boolean> f : fs.allFields(t, new LinkedHashSet<>()).entrySet()) {
            if (!javaFields.contains(f.getKey()) && !t.indexSignature()) {
                violations.add(new Violation("RESP_FIELD|" + id + "|" + t.name() + "." + f.getKey(),
                        id + ": " + t.name() + "." + f.getKey() + (f.getValue() ? " (optional)" : "")
                                + " is not in " + js.element().getSimpleName() + " " + javaFields + " @" + c.where()));
            }
        }
    }

    private boolean tsKnown(FrontendSources fs, TsType ts, FrontendSources.Call c) {
        return ts.name() != null && fs.resolve(ts.name(), c.file()) != null;
    }

    private void checkBody(FrontendSources fs, FrontendSources.Call c, Endpoint e, List<Violation> violations) {
        if (c.body() == null) {
            return;
        }
        Class<?> bodyClass = null;
        for (java.lang.reflect.Parameter p : e.method().getParameters()) {
            if (p.isAnnotationPresent(RequestBody.class)) {
                bodyClass = p.getType();
            }
        }
        if (bodyClass == null || skipFields(bodyClass)) {
            return;
        }
        Set<String> keys;
        boolean complete;
        String b = c.body();
        if (b.startsWith("{")) {
            keys = literalKeys(b);
            complete = !b.contains("...");
        } else if (b.matches("[A-Za-z_$][\\w$]*")) {
            String before = c.text().substring(0, c.literalStart());
            Matcher header = Pattern.compile(
                    "\\n {2}(?:(?:public|private|protected|readonly|async|get|set)\\s+)*[A-Za-z_$][\\w$]*\\s*(?:<[^>]*>)?\\s*\\(")
                    .matcher(before);
            int methodStart = 0;
            while (header.find()) {
                methodStart = header.start();
            }
            Matcher m = Pattern.compile("\\b" + Pattern.quote(b) + "\\??\\s*:\\s*([A-Za-z_$][\\w$]*)(?:<[^>]*>)?")
                    .matcher(before.substring(methodStart));
            String type = null;
            while (m.find()) {
                type = m.group(1);
            }
            FrontendSources.TsInterface t = type == null ? null : fs.resolve(type, c.file());
            if (t == null) {
                return;
            }
            keys = fs.allFields(t, new LinkedHashSet<>()).keySet();
            complete = !t.indexSignature();
        } else {
            return;
        }
        Set<String> javaFields = javaFields(bodyClass);
        String id = c.verb() + " " + c.url();
        for (String k : keys) {
            if (!javaFields.contains(k)) {
                violations.add(new Violation("BODY_IGNORED|" + id + "|" + k,
                        id + ": the frontend sends '" + k + "' but " + bodyClass.getSimpleName() + " has "
                                + javaFields + " @" + c.where()));
            }
        }
        if (complete) {
            for (String r : requiredFields(bodyClass)) {
                if (!keys.contains(r)) {
                    violations.add(new Violation("BODY_REQUIRED|" + id + "|" + r,
                            id + ": " + bodyClass.getSimpleName() + "." + r + " is required but the frontend body "
                                    + keys + " does not carry it @" + c.where()));
                }
            }
        }
    }

    /**
     * Query-parameter names the frontend builds for a call (via HttpParams.set/append, params['x'] and an inline
     * params object) against what the handler binds: @RequestParam names, Pageable's page/size/sort, and the fields
     * of an unannotated model-attribute object. Only methods with a single HTTP call are judged, since the keys of
     * a method that makes several calls cannot be attributed to one of them.
     */
    private void checkParams(FrontendSources.Call c, Endpoint e, List<Violation> violations) {
        Set<String> sent = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\\.(?:set|append)\\(\\s*'(\\w+)'|params\\[\\s*'(\\w+)'\\s*\\]").matcher(c.methodText());
        while (m.find()) {
            sent.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        Matcher obj = Pattern.compile("params\\s*:\\s*\\{([^}]*)\\}").matcher(c.methodText());
        while (obj.find()) {
            sent.addAll(literalKeys("{" + obj.group(1) + "}"));
        }
        if (sent.isEmpty()) {
            return;
        }
        paramChecked++;
        Set<String> accepted = new LinkedHashSet<>();
        for (java.lang.reflect.Parameter p : e.method().getParameters()) {
            org.springframework.web.bind.annotation.RequestParam rp = p
                    .getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
            if (rp != null) {
                if (Map.class.isAssignableFrom(p.getType())) {
                    return;
                }
                String n = !rp.name().isEmpty() ? rp.name() : !rp.value().isEmpty() ? rp.value() : p.getName();
                accepted.add(n);
            } else if (org.springframework.data.domain.Pageable.class.isAssignableFrom(p.getType())) {
                accepted.addAll(Set.of("page", "size", "sort"));
            } else if (p.getAnnotations().length == 0 && !skipFields(p.getType())
                    && !p.getType().getName().startsWith("jakarta.")) {
                accepted.addAll(javaFields(p.getType()));
            }
        }
        String id = c.verb() + " " + c.url();
        for (String k : sent) {
            if (!accepted.contains(k)) {
                violations.add(new Violation("PARAM_UNKNOWN|" + id + "|" + k,
                        id + ": the frontend sends query parameter '" + k + "' but the handler binds " + accepted
                                + " @" + c.where()));
            }
        }
    }

    private static Set<String> literalKeys(String objectLiteral) {
        Set<String> keys = new LinkedHashSet<>();
        String inner = objectLiteral.substring(1, objectLiteral.lastIndexOf('}') < 0 ? objectLiteral.length()
                : objectLiteral.lastIndexOf('}'));
        for (String part : FrontendSources.splitTop(inner, ',')) {
            Matcher m = Pattern.compile("^\\s*(?:'([^']+)'|([A-Za-z_$][\\w$]*))\\s*(?::|$)").matcher(part);
            if (m.find()) {
                keys.add(m.group(1) != null ? m.group(1) : m.group(2));
            }
        }
        return keys;
    }

    private void checkEnums(FrontendSources fs, Map<String, Class<?>> enums, List<Violation> violations) {
        for (Map.Entry<String, Set<String>> u : fs.stringUnions.entrySet()) {
            Class<?> en = enums.get(u.getKey());
            if (en == null) {
                continue;
            }
            Set<String> constants = new LinkedHashSet<>();
            for (Object k : en.getEnumConstants()) {
                constants.add(((Enum<?>) k).name());
            }
            for (String v : u.getValue()) {
                if (!constants.contains(v)) {
                    violations.add(new Violation("ENUM|" + u.getKey() + "|" + v,
                            "frontend type " + u.getKey() + " allows '" + v + "' but enum " + en.getName()
                                    + " has " + constants));
                }
            }
        }
    }

    // ------------------------------------------------------------------ type helpers

    private static JavaShape shape(Type t) {
        Type u = unwrap(t);
        Class<?> raw = raw(u);
        if (raw == null || raw == void.class || raw == Void.class) {
            return new JavaShape(Kind.NONE, null, null);
        }
        if (raw.getName().equals("org.springframework.data.domain.Page")) {
            return new JavaShape(Kind.PAGE, raw(typeArg(u, 0)), SPRING_PAGE_JSON);
        }
        if (raw.getSimpleName().equals("PageResponse")) {
            return new JavaShape(Kind.PAGE, raw(typeArg(u, 0)), javaFields(raw));
        }
        if (Collection.class.isAssignableFrom(raw)) {
            return new JavaShape(Kind.ARRAY, raw(typeArg(u, 0)), null);
        }
        return new JavaShape(Kind.OBJECT, raw, null);
    }

    private static Type unwrap(Type t) {
        Class<?> raw = raw(t);
        if (raw != null && raw.getSimpleName().equals("ResponseEntity") && typeArg(t, 0) != null) {
            return unwrap(typeArg(t, 0));
        }
        return t;
    }

    private static Type typeArg(Type t, int i) {
        if (t instanceof ParameterizedType p && p.getActualTypeArguments().length > i) {
            Type a = p.getActualTypeArguments()[i];
            return a instanceof WildcardType w ? w.getUpperBounds()[0] : a;
        }
        return null;
    }

    private static Class<?> raw(Type t) {
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType p) {
            return raw(p.getRawType());
        }
        return null;
    }

    private static TsType tsType(String generic) {
        String g = generic.trim();
        Matcher m = Pattern.compile("^(?:Array|ReadonlyArray)<\\s*([A-Za-z_$][\\w$]*)\\s*>$").matcher(g);
        if (m.find()) {
            return new TsType(Kind.ARRAY, m.group(1), null);
        }
        m = Pattern.compile("^\\{\\s*content\\s*:\\s*([A-Za-z_$][\\w$]*)\\[\\]\\s*\\}$").matcher(g);
        if (m.find()) {
            return new TsType(Kind.PAGE, m.group(1), null);
        }
        m = Pattern.compile("^([A-Za-z_$][\\w$]*)\\[\\]$").matcher(g);
        if (m.find()) {
            return new TsType(Kind.ARRAY, m.group(1), null);
        }
        m = Pattern.compile("^(Page|PageResponse)<\\s*([A-Za-z_$][\\w$]*)\\s*>$").matcher(g);
        if (m.find()) {
            return new TsType(Kind.PAGE, m.group(2), m.group(1));
        }
        if (g.matches("[A-Za-z_$][\\w$]*")) {
            return new TsType(Kind.OBJECT, g, null);
        }
        return new TsType(Kind.OBJECT, null, null);
    }

    private static boolean skipFields(Class<?> c) {
        return c == null || c.isInterface() || c == Object.class || Map.class.isAssignableFrom(c) || c.isEnum()
                || c.isPrimitive() || c.isArray() || c.getName().startsWith("java.")
                || c.getName().startsWith("com.fasterxml") || c.getName().startsWith("org.springframework");
    }

    private static Set<String> javaFields(Class<?> c) {
        Set<String> f = new LinkedHashSet<>();
        if (c.isRecord()) {
            for (RecordComponent rc : c.getRecordComponents()) {
                f.add(rc.getName());
            }
        } else {
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field fd : k.getDeclaredFields()) {
                    if (!Modifier.isStatic(fd.getModifiers())) {
                        f.add(fd.getName());
                    }
                }
            }
        }
        return f;
    }

    private static Set<String> requiredFields(Class<?> c) {
        Set<String> req = new LinkedHashSet<>();
        if (!c.isRecord()) {
            return req;
        }
        for (RecordComponent rc : c.getRecordComponents()) {
            if (rc.getType().isPrimitive()) {
                continue;
            }
            boolean r = hasRequired(rc.getAccessor());
            if (!r) {
                try {
                    r = hasRequired(c.getDeclaredField(rc.getName()));
                } catch (NoSuchFieldException ignored) {
                    // no backing field annotation
                }
            }
            if (r) {
                req.add(rc.getName());
            }
        }
        return req;
    }

    private static boolean hasRequired(AnnotatedElement e) {
        for (Class<? extends Annotation> a : List.of(NotNull.class, NotBlank.class, NotEmpty.class)) {
            if (e.isAnnotationPresent(a)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ config, allowlist, report

    private static String readConfigBase() throws IOException {
        String json = Files.readString(FRONTEND.resolve("public").resolve("config.json"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("\"apiBaseUrl\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertThat(m.find()).as("public/config.json must define apiBaseUrl").isTrue();
        return m.group(1);
    }

    private static String readCodeDefaultBase() throws IOException {
        String ts = Files.readString(FRONTEND.resolve("src/app/core/config/app-config.service.ts"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("DEFAULTS[^=]*=\\s*\\{\\s*apiBaseUrl:\\s*'([^']*)'").matcher(ts);
        assertThat(m.find()).as("AppConfigService must define DEFAULTS.apiBaseUrl").isTrue();
        return m.group(1);
    }

    private static Map<String, String> readAllowlist() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        if (!Files.exists(ALLOWLIST)) {
            return out;
        }
        for (String line : Files.readAllLines(ALLOWLIST, StandardCharsets.UTF_8)) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int hash = t.indexOf("  #");
            String key = (hash < 0 ? t : t.substring(0, hash)).trim();
            String reason = hash < 0 ? "" : t.substring(hash + 3).trim();
            assertThat(reason).as("allowlist entry '%s' needs a reason after '  #'", key).isNotBlank();
            out.put(key, reason);
        }
        return out;
    }

    private void writeReport(FrontendSources fs, int endpointCount, int matched, int weak, int unresolved,
            List<Violation> violations, Map<String, String> allowed, List<String> notes, List<String> stale)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("API contract report\n===================\n");
        sb.append("frontend calls scanned : ").append(fs.calls.size()).append('\n');
        sb.append("backend endpoints      : ").append(endpointCount).append('\n');
        sb.append("matched                : ").append(matched).append(" (weak: ").append(weak).append(")\n");
        sb.append("unresolved             : ").append(unresolved).append('\n');
        sb.append("violations             : ").append(violations.size()).append(" (allowlisted: ")
                .append(violations.stream().filter(v -> allowed.containsKey(v.key())).count()).append(")\n");
        sb.append("stale allowlist entries: ").append(stale.size()).append("\n\nVIOLATIONS\n----------\n");
        for (Violation v : violations) {
            sb.append(allowed.containsKey(v.key()) ? "[allowed] " : "[FAIL]    ").append(v.key()).append("\n          ")
                    .append(v.detail()).append('\n');
        }
        sb.append("\nOTHER /api LITERALS (not in a scanned call)\n------------------------------------------\n");
        fs.otherApiLiterals.forEach(l -> sb.append(l).append('\n'));
        sb.append("\nNOTES\n-----\n");
        notes.forEach(n -> sb.append(n).append('\n'));
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, sb.toString(), StandardCharsets.UTF_8);
    }
}
