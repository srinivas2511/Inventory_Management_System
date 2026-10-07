package com.springmfg.ims.contract;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Makes the UAT checklist auditable mechanically (E2E finding G-3): every test ID in docs/UAT_CHECKLIST.md must be backed
 * by an end-to-end test, either because a test title in frontend/e2e names the ID, or because frontend/e2e/uat-map.json maps
 * it to a spec and a test title that really exist. A checklist row with no e2e test, a map entry pointing at a test that has
 * been renamed or deleted, and a map entry for an ID that is not on the checklist all fail the build.
 */
class UatTraceabilityTest {

    private static final Path UAT = Path.of("..", "docs", "UAT_CHECKLIST.md");
    private static final Path E2E = Path.of("..", "frontend", "e2e");
    private static final Path MAP = E2E.resolve("uat-map.json");
    private static final Pattern ROW_ID = Pattern.compile("^\\|\\s*([A-Z]{3}-\\d{2}[a-z]?)\\s*\\|");
    private static final Pattern TITLE_LINE = Pattern.compile("\\btest(\\.describe)?(\\.serial)?\\s*\\(|tag\\s*:");

    @Test
    @DisplayName("Every UAT checklist ID is covered by a named e2e test or a valid mapping")
    void everyChecklistIdHasAnE2eTest() throws IOException {
        List<String> ids = checklistIds();
        assertThat(ids.size()).as("the checklist parser should find the UAT rows").isGreaterThan(50);

        Map<Path, String> specs = specs();
        Set<String> named = new LinkedHashSet<>();
        for (String text : specs.values()) {
            for (String line : text.split("\\R")) {
                if (!TITLE_LINE.matcher(line).find()) {
                    continue;
                }
                for (String id : ids) {
                    if (Pattern.compile("\\b" + Pattern.quote(id) + "\\b").matcher(line).find()) {
                        named.add(id);
                    }
                }
            }
        }

        JsonNode map = new ObjectMapper().readTree(Files.readString(MAP, StandardCharsets.UTF_8));
        List<String> problems = new ArrayList<>();
        Set<String> mapped = new LinkedHashSet<>();
        map.fieldNames().forEachRemaining(key -> {
            if (key.startsWith("_")) {
                return;
            }
            mapped.add(key);
            if (!ids.contains(key)) {
                problems.add(key + ": mapped in uat-map.json but not a row of UAT_CHECKLIST.md");
            }
            JsonNode entries = map.get(key);
            if (!entries.isArray() || entries.isEmpty()) {
                problems.add(key + ": the map entry must be a non-empty array of {spec, test}");
                return;
            }
            for (JsonNode e : entries) {
                Path spec = E2E.resolve(e.path("spec").asText(""));
                String title = e.path("test").asText("");
                if (!Files.isRegularFile(spec)) {
                    problems.add(key + ": spec file not found: " + spec.normalize());
                } else if (title.isBlank() || !readSafely(spec).contains(title)) {
                    problems.add(key + ": no test titled \"" + title + "\" in " + e.path("spec").asText());
                }
            }
        });

        List<String> uncovered = new ArrayList<>();
        for (String id : ids) {
            if (!named.contains(id) && !mapped.contains(id)) {
                uncovered.add(id);
            }
        }
        if (!uncovered.isEmpty()) {
            problems.add("UAT IDs with no e2e test (name the ID in a test title or map it in uat-map.json): " + uncovered);
        }
        assertThat(problems).as("UAT traceability problems").isEmpty();
    }

    private static List<String> checklistIds() throws IOException {
        List<String> ids = new ArrayList<>();
        for (String line : Files.readAllLines(UAT, StandardCharsets.UTF_8)) {
            Matcher m = ROW_ID.matcher(line);
            if (m.find() && !ids.contains(m.group(1))) {
                ids.add(m.group(1));
            }
        }
        return ids;
    }

    private static Map<Path, String> specs() throws IOException {
        Map<Path, String> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.walk(E2E)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".ts"))::iterator) {
                out.put(p, readSafely(p));
            }
        }
        assertThat(out).as("the e2e specs should be found under frontend/e2e").isNotEmpty();
        return out;
    }

    private static String readSafely(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(p.toString(), e);
        }
    }
}
