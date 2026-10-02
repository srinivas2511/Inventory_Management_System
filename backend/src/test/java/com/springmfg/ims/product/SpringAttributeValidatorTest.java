package com.springmfg.ims.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.springmfg.ims.common.exception.Problems;
import com.springmfg.ims.product.AttributeDefinition.DataType;

/** The catalogue-driven validator, with hand-built definitions (no database). */
class SpringAttributeValidatorTest {

    private final AttributeDefinitionRepository repository = mock(AttributeDefinitionRepository.class);
    private final SpringAttributeValidator validator = new SpringAttributeValidator(repository);
    private final List<AttributeDefinition> catalogue = new ArrayList<>();

    private AttributeDefinition define(String code, DataType type, boolean required, String min, String max, String... enumValues) {
        AttributeDefinition d = new AttributeDefinition();
        ReflectionTestUtils.setField(d, "springType", SpringType.TORSION);
        ReflectionTestUtils.setField(d, "attributeCode", code);
        ReflectionTestUtils.setField(d, "label", "Label of " + code);
        ReflectionTestUtils.setField(d, "dataType", type);
        ReflectionTestUtils.setField(d, "unit", "mm");
        ReflectionTestUtils.setField(d, "required", required);
        ReflectionTestUtils.setField(d, "minValue", min == null ? null : new BigDecimal(min));
        ReflectionTestUtils.setField(d, "maxValue", max == null ? null : new BigDecimal(max));
        ReflectionTestUtils.setField(d, "enumValues", enumValues.length == 0 ? null : List.of(enumValues));
        catalogue.add(d);
        when(repository.findBySpringTypeOrderByDisplayOrderAscAttributeCodeAsc(any())).thenReturn(catalogue);
        return d;
    }

    private List<String> problems(SpringType type, Map<String, Object> core, Map<String, Object> specs, boolean complete) {
        return validator.validate(type, core, specs, complete).stream().map(e -> e.field() + ": " + e.message()).toList();
    }

    private List<String> fields(SpringType type, Map<String, Object> core, Map<String, Object> specs, boolean complete) {
        return validator.validate(type, core, specs, complete).stream().map(Problems.FieldError::field).toList();
    }

    @Test
    void requiredAttributesAreOnlyEnforcedForACompleteProduct() {
        define("wireDiameter", DataType.NUMBER, true, "0.001", null);
        define("legLength", DataType.NUMBER, true, "0", null);
        define("torque", DataType.NUMBER, false, "0", null);

        assertThat(problems(SpringType.TORSION, new HashMap<>(), Map.of(), false)).isEmpty(); // a draft may be unfinished
        assertThat(fields(SpringType.TORSION, new HashMap<>(), Map.of(), true)).containsExactlyInAnyOrder("wireDiameter", "specifications.legLength");
        assertThat(problems(SpringType.TORSION, Map.of("wireDiameter", new BigDecimal("1.5")), Map.of("legLength", 25), true)).isEmpty();
    }

    @Test
    void columnBackedAttributesAreReadFromTheColumnsAndOthersFromSpecifications() {
        define("wireDiameter", DataType.NUMBER, true, null, null); // a product column
        define("legAngle", DataType.NUMBER, true, null, null);     // stored in specifications
        Map<String, Object> core = new HashMap<>();
        core.put("wireDiameter", BigDecimal.ONE);

        assertThat(fields(SpringType.TORSION, core, Map.of(), true)).containsExactly("specifications.legAngle");
        assertThat(problems(SpringType.TORSION, core, Map.of("legAngle", 90), true)).isEmpty();
        // a column-backed code inside specifications is refused: it belongs at the top level
        assertThat(problems(SpringType.TORSION, core, Map.of("legAngle", 90, "wireDiameter", 2), true))
                .containsExactly("specifications.wireDiameter: 'wireDiameter' is a core product field: send it at the top level, not in specifications.");
    }

    @Test
    void numbersMustBeNumbersWithinTheLimits() {
        define("legAngle", DataType.NUMBER, false, "0", "360");
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("legAngle", 90), true)).isEmpty();
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("legAngle", 0), true)).isEmpty();  // limits are inclusive
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("legAngle", 360), true)).isEmpty();
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("legAngle", 361), true))
                .containsExactly("specifications.legAngle: Label of legAngle: must be at most 360 mm.");
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("legAngle", -0.5), true)).hasSize(1);
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("legAngle", "90"), true)).containsExactly("specifications.legAngle: Label of legAngle: must be a number.");
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("legAngle", true), true)).hasSize(1);
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("legAngle", new BigDecimal("359.999")), true)).isEmpty();
    }

    @Test
    void enumsAndTextAndBooleansAreChecked() {
        define("hookType", DataType.ENUM, false, null, null, "MACHINE_HOOK", "SIDE_HOOK");
        define("note", DataType.TEXT, false, null, null);
        define("heatTreated", DataType.BOOLEAN, false, null, null);

        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("hookType", "SIDE_HOOK", "note", "ok", "heatTreated", true), true)).isEmpty();
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("hookType", "side_hook"), true))
                .containsExactly("specifications.hookType: Label of hookType: must be one of MACHINE_HOOK, SIDE_HOOK.");
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("hookType", 3), true)).hasSize(1);
        assertThat(fields(SpringType.TORSION, Map.of(), Map.of("note", " "), true)).containsExactly("specifications.note");
        assertThat(fields(SpringType.TORSION, Map.of(), Map.of("note", "x".repeat(201)), true)).containsExactly("specifications.note");
        assertThat(fields(SpringType.TORSION, Map.of(), Map.of("heatTreated", "yes"), true)).containsExactly("specifications.heatTreated");
    }

    @Test
    void anEnumColumnIsValidatedAgainstItsDefinition() {
        define("endType", DataType.ENUM, false, null, null, "PLAIN", "CLOSED_GROUND");
        assertThat(problems(SpringType.TORSION, Map.of("endType", "CLOSED_GROUND"), Map.of(), true)).isEmpty();
        assertThat(fields(SpringType.TORSION, Map.of("endType", "FANCY"), Map.of(), true)).containsExactly("endType");
    }

    @Test
    void unknownKeysAreRejectedAndTheMessageListsWhatIsAllowed() {
        define("legAngle", DataType.NUMBER, false, null, null);
        define("torque", DataType.NUMBER, false, null, null);
        assertThat(problems(SpringType.TORSION, Map.of(), Map.of("colour", "red"), true)).containsExactly(
                "specifications.colour: Unknown attribute 'colour' for TORSION. Allowed: legAngle, torque.");
    }

    @Test
    void customSpringsAcceptFreeFormSpecificationsWithinLimits() {
        assertThat(problems(SpringType.CUSTOM, Map.of(), Map.of("anyKey", "x", "turns", 3, "special", true, "ratio", 1.5), true)).isEmpty();
        assertThat(fields(SpringType.CUSTOM, Map.of(), Map.of("bad key", "x"), true)).containsExactly("specifications.bad key");
        assertThat(fields(SpringType.CUSTOM, Map.of(), Map.of("1abc", "x"), true)).containsExactly("specifications.1abc");
        assertThat(fields(SpringType.CUSTOM, Map.of(), Map.of("nested", Map.of("a", 1)), true)).containsExactly("specifications.nested");
        assertThat(fields(SpringType.CUSTOM, Map.of(), Map.of("long", "x".repeat(201)), true)).containsExactly("specifications.long");
        assertThat(fields(SpringType.CUSTOM, Map.of(), Map.of("wireDiameter", 2), true)).containsExactly("specifications.wireDiameter");

        Map<String, Object> tooMany = new HashMap<>();
        for (int i = 0; i < 31; i++) {
            tooMany.put("k" + i, i);
        }
        assertThat(fields(SpringType.CUSTOM, Map.of(), tooMany, true)).containsExactly("specifications");
    }

    @Test
    void definitionsAddedToACustomTypeStillApply() {
        define("grade", DataType.TEXT, true, null, null); // a catalogue row for CUSTOM (data, not code)
        assertThat(fields(SpringType.CUSTOM, Map.of(), Map.of("anything", 1), true)).containsExactly("specifications.grade");
        assertThat(problems(SpringType.CUSTOM, Map.of(), Map.of("anything", 1, "grade", "A"), true)).isEmpty();
    }

    @Test
    void numbersAreStoredExactlyAsWritten() {
        Map<String, Object> normalised = SpringAttributeValidator.normalise(Map.of("a", 2.5, "b", 3, "c", "text", "d", true));
        assertThat(normalised.get("a")).isEqualTo(new BigDecimal("2.5"));
        assertThat(normalised.get("b")).isEqualTo(new BigDecimal("3"));
        assertThat(normalised.get("c")).isEqualTo("text");
        assertThat(normalised.get("d")).isEqualTo(true);
        assertThat(SpringAttributeValidator.normalise(null)).isEmpty();
    }

    @Test
    void coreCodesMatchTheProductColumnsTheCatalogueMayReference() {
        assertThat(SpringAttributeValidator.CORE_CODES).containsExactlyInAnyOrder("wireDiameter", "outerDiameter", "innerDiameter",
                "freeLength", "numberOfCoils", "activeCoils", "springRate", "maxLoad", "minLoad", "workingLength", "solidHeight", "endType");
    }
}
