package com.springmfg.ims.contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Static scan of the Angular sources: every {@code HttpClient}/{@code ApiService} call (verb, URL with the file's base
 * resolved, response generic, request body), every TypeScript interface and every string-literal union type. It is a
 * text scan, not a TypeScript compiler, so what it cannot resolve is reported as unresolved instead of guessed.
 */
final class FrontendSources {

    record Call(String file, int line, String verb, String receiver, String rawUrl, String url, boolean resolved,
            boolean dynamicSuffix, String generic, String body, int literalStart, String text, String methodKey,
            String methodText) {
        String where() {
            return file + ":" + line;
        }
    }

    record TsInterface(String name, String file, Set<String> extendsNames, Map<String, Boolean> fields,
            boolean indexSignature) {}

    final List<Call> calls = new ArrayList<>();
    final Map<String, List<TsInterface>> interfaces = new TreeMap<>();
    final Map<String, Set<String>> stringUnions = new TreeMap<>();
    final List<String> otherApiLiterals = new ArrayList<>();

    private static final Pattern CALL = Pattern.compile("((?:this\\.)?[A-Za-z_$][\\w$]*)\\.(get|post|put|patch|delete)(?=\\s*[<(])");
    private static final Set<String> RECEIVERS = Set.of("http", "api");
    /** A class member header at prettier's two-space indent: where one method ends and the next begins. */
    static final Pattern METHOD_HEADER = Pattern.compile(
            "\\n {2}(?:(?:public|private|protected|readonly|async|get|set|static)\\s+)*[A-Za-z_$][\\w$]*\\s*(?:<[^>]*>)?\\s*\\(");
    private static final Pattern API_LITERAL = Pattern.compile("['\"`]((?:\\$\\{[^}]*\\})?/api/[^'\"`]*)['\"`]");

    static FrontendSources scan(Path appDir, String apiBase) throws IOException {
        FrontendSources fs = new FrontendSources();
        List<Path> files;
        try (Stream<Path> s = Files.walk(appDir)) {
            files = s.filter(p -> p.toString().endsWith(".ts") && !p.toString().endsWith(".spec.ts")).sorted().toList();
        }
        for (Path f : files) {
            String rel = appDir.relativize(f).toString().replace('\\', '/');
            String text = Files.readString(f, StandardCharsets.UTF_8);
            fs.scanText(rel, text, apiBase);
        }
        return fs;
    }

    void scanText(String file, String text, String apiBase) {
        parseInterfaces(file, text);
        parseUnions(text);
        String fileBase = fileBase(text, apiBase);
        List<Integer> headers = new ArrayList<>();
        Matcher hm = METHOD_HEADER.matcher(text);
        while (hm.find()) {
            headers.add(hm.start());
        }
        Set<Integer> consumed = new LinkedHashSet<>();
        Matcher m = CALL.matcher(text);
        while (m.find()) {
            String receiver = m.group(1);
            String last = receiver.substring(receiver.lastIndexOf('.') + 1);
            if (!RECEIVERS.contains(last)) {
                continue;
            }
            int i = m.end();
            String generic = null;
            i = skipWs(text, i);
            if (i < text.length() && text.charAt(i) == '<') {
                int close = matchAngle(text, i);
                if (close < 0) {
                    continue;
                }
                generic = text.substring(i + 1, close).trim();
                i = skipWs(text, close + 1);
            }
            if (i >= text.length() || text.charAt(i) != '(') {
                continue;
            }
            i = skipWs(text, i + 1);
            int line = lineOf(text, m.start());
            char c = i < text.length() ? text.charAt(i) : ' ';
            String raw;
            String url = null;
            boolean resolved = false;
            boolean dyn = false;
            int end;
            if (c == '\'' || c == '"' || c == '`') {
                Literal lit = readLiteral(text, i, fileBase, apiBase);
                raw = text.substring(i, lit.end);
                end = lit.end;
                consumed.add(i);
                String path = lit.value;
                if (path != null) {
                    boolean viaApiService = last.equals("api");
                    url = viaApiService ? joinBase(apiBase, path) : path;
                    resolved = !lit.unresolvedBase;
                }
                dyn = lit.dynamicSuffix;
            } else {
                end = readArgEnd(text, i);
                raw = text.substring(i, Math.max(i, end)).trim();
                if (fileBase != null && (raw.equals("this.base") || raw.equals("base") || raw.equals("this.base()"))) {
                    url = fileBase;
                    resolved = true;
                }
            }
            String body = null;
            int j = skipWs(text, end);
            if (j < text.length() && text.charAt(j) == ',') {
                int bs = skipWs(text, j + 1);
                int be = readArgEnd(text, bs);
                if (be > bs) {
                    body = text.substring(bs, be).trim();
                }
            }
            int methodStart = 0;
            int methodEnd = text.length();
            for (int h : headers) {
                if (h <= i) {
                    methodStart = h;
                } else {
                    methodEnd = h;
                    break;
                }
            }
            calls.add(new Call(file, line, m.group(2).toUpperCase(), receiver, raw, url, resolved, dyn, generic, body, i,
                    text, file + "#" + methodStart, text.substring(methodStart, methodEnd)));
        }
        Matcher lm = API_LITERAL.matcher(text);
        while (lm.find()) {
            if (!consumed.contains(lm.start())) {
                otherApiLiterals.add(file + ":" + lineOf(text, lm.start()) + " " + lm.group(1));
            }
        }
    }

    // ---------------------------------------------------------------- URL literals

    private record Literal(String value, int end, boolean dynamicSuffix, boolean unresolvedBase) {}

    private static Literal readLiteral(String s, int start, String fileBase, String apiBase) {
        char q = s.charAt(start);
        StringBuilder out = new StringBuilder();
        boolean dyn = false;
        boolean unresolvedBase = false;
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                out.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == q) {
                i++;
                break;
            }
            if (q == '`' && c == '$' && i + 1 < s.length() && s.charAt(i + 1) == '{') {
                int close = matchBrace(s, i + 1);
                String expr = s.substring(i + 2, close).trim();
                String r = resolveExpr(expr, fileBase, apiBase);
                if (r != null) {
                    out.append(r);
                } else if (isBaseLike(expr)) {
                    unresolvedBase = true;
                } else if (out.length() == 0 || out.charAt(out.length() - 1) == '/') {
                    out.append("{}");
                } else if (out.indexOf("?") >= 0) {
                    // inside the query string: not part of the path
                } else {
                    dyn = true;
                }
                i = close + 1;
                continue;
            }
            out.append(c);
            i++;
        }
        String v = out.toString();
        int qm = v.indexOf('?');
        if (qm >= 0) {
            v = v.substring(0, qm);
        }
        while (v.length() > 1 && v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return new Literal(v, i, dyn, unresolvedBase);
    }

    private static boolean isBaseLike(String e) {
        return e.equals("this.base") || e.equals("base") || e.equals("this.base()") || e.equals("this.baseUrl")
                || e.equals("this.url") || e.contains("apiBaseUrl");
    }

    private static String resolveExpr(String e, String fileBase, String apiBase) {
        if (e.contains("apiBaseUrl")) {
            return apiBase;
        }
        if (e.equals("this.base") || e.equals("base") || e.equals("this.base()") || e.equals("this.baseUrl")) {
            return fileBase;
        }
        return null;
    }

    static String joinBase(String base, String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        return (base == null ? "" : base.replaceAll("/+$", "")) + p;
    }

    /** What {@code this.base} means in this file: a literal path, or the configured apiBaseUrl. */
    static String fileBase(String text, String apiBase) {
        Matcher m = Pattern.compile("(?:private|protected)\\s+(?:readonly\\s+)?base\\s*(?::\\s*string)?\\s*=\\s*([^;]+);")
                .matcher(text);
        if (m.find()) {
            return evalSimple(m.group(1), apiBase);
        }
        m = Pattern.compile("get\\s+base\\s*\\(\\)\\s*(?::\\s*string)?\\s*\\{\\s*return\\s*([^;]+);").matcher(text);
        if (m.find()) {
            return evalSimple(m.group(1), apiBase);
        }
        return null;
    }

    private static String evalSimple(String expr, String apiBase) {
        String e = expr.trim();
        if (e.startsWith("'") || e.startsWith("\"")) {
            return e.substring(1, e.length() - 1);
        }
        if (e.startsWith("`")) {
            String t = e.substring(1, e.length() - 1);
            t = t.replaceAll("\\$\\{[^}]*apiBaseUrl[^}]*\\}", Matcher.quoteReplacement(apiBase == null ? "" : apiBase));
            return t.replaceAll("\\$\\{[^}]*\\}", "{}");
        }
        if (e.contains("apiBaseUrl")) {
            return apiBase;
        }
        return null;
    }

    // ---------------------------------------------------------------- source scanning helpers

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int lineOf(String s, int idx) {
        int line = 1;
        for (int k = 0; k < idx && k < s.length(); k++) {
            if (s.charAt(k) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static int matchAngle(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length() && i < open + 2000; i++) {
            char c = s.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>' && !(i > 0 && s.charAt(i - 1) == '=')) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int matchBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return s.length() - 1;
    }

    /** End (exclusive) of an argument expression starting at {@code start}: the next top-level ',' or ')'. */
    private static int readArgEnd(String s, int start) {
        int depth = 0;
        int i = start;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                i = skipString(s, i);
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                return i;
            }
            i++;
        }
        return i;
    }

    private static int skipString(String s, int start) {
        char q = s.charAt(start);
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (q == '`' && c == '$' && i + 1 < s.length() && s.charAt(i + 1) == '{') {
                i = matchBrace(s, i + 1) + 1;
                continue;
            }
            if (c == q) {
                return i + 1;
            }
            i++;
        }
        return i;
    }

    // ---------------------------------------------------------------- interfaces and unions

    private void parseInterfaces(String file, String text) {
        Matcher m = Pattern.compile("(?:export\\s+)?interface\\s+(\\w+)(?:<[^>{]*>)?(?:\\s+extends\\s+([^{]+?))?\\s*\\{")
                .matcher(text);
        while (m.find()) {
            int open = m.end() - 1;
            int close = matchBrace(text, open);
            String body = stripComments(text.substring(open + 1, close));
            Set<String> ext = new LinkedHashSet<>();
            if (m.group(2) != null) {
                for (String part : splitTop(m.group(2), ',')) {
                    ext.add(part.trim().replaceAll("<.*", ""));
                }
            }
            Map<String, Boolean> fields = new LinkedHashMap<>();
            boolean index = false;
            for (String member : splitTop(body, ';')) {
                String t = member.trim();
                if (t.isEmpty()) {
                    continue;
                }
                Matcher f = Pattern.compile("^(?:readonly\\s+)?(\\w+)(\\?)?\\s*:").matcher(t);
                if (f.find()) {
                    fields.put(f.group(1), f.group(2) != null);
                } else if (t.startsWith("[")) {
                    index = true;
                }
            }
            interfaces.computeIfAbsent(m.group(1), k -> new ArrayList<>())
                    .add(new TsInterface(m.group(1), file, ext, fields, index));
        }
    }

    private void parseUnions(String text) {
        Matcher m = Pattern.compile("(?:export\\s+)?type\\s+(\\w+)\\s*=\\s*((?:\\s*\\|?\\s*'[^']*')+)\\s*;").matcher(text);
        while (m.find()) {
            Set<String> vals = new LinkedHashSet<>();
            Matcher v = Pattern.compile("'([^']*)'").matcher(m.group(2));
            while (v.find()) {
                vals.add(v.group(1));
            }
            stringUnions.merge(m.group(1), vals, (a, b) -> {
                Set<String> u = new LinkedHashSet<>(a);
                u.addAll(b);
                return u;
            });
        }
    }

    private static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)(?<![:'\"])//.*$", "");
    }

    /** Splits on a separator at bracket depth 0 (object types, generics and parentheses nest). */
    static List<String> splitTop(String s, char sep) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int last = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                i = skipString(s, i) - 1;
            } else if (c == '{' || c == '(' || c == '[' || c == '<' && !isArrow(s, i)) {
                depth++;
            } else if (c == '}' || c == ')' || c == ']' || c == '>' && !(i > 0 && s.charAt(i - 1) == '=')) {
                depth = Math.max(0, depth - 1);
            } else if (c == sep && depth == 0) {
                parts.add(s.substring(last, i));
                last = i + 1;
            }
        }
        parts.add(s.substring(last));
        return parts;
    }

    private static boolean isArrow(String s, int i) {
        return false;
    }

    /** All fields of an interface including those inherited from interfaces we know about. */
    Map<String, Boolean> allFields(TsInterface t, Set<String> seen) {
        Map<String, Boolean> out = new LinkedHashMap<>(t.fields());
        if (!seen.add(t.name())) {
            return out;
        }
        for (String e : t.extendsNames()) {
            List<TsInterface> parents = interfaces.get(e);
            if (parents != null && parents.size() == 1) {
                out.putAll(allFields(parents.get(0), seen));
            }
        }
        return out;
    }

    /** The unique interface with this name, preferring the one in {@code file}; null when unknown or ambiguous. */
    TsInterface resolve(String name, String file) {
        List<TsInterface> list = interfaces.get(name);
        if (list == null) {
            return null;
        }
        for (TsInterface t : list) {
            if (t.file().equals(file)) {
                return t;
            }
        }
        return list.size() == 1 ? list.get(0) : null;
    }
}
