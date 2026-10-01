package com.springmfg.ims.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

/** Bean-validation rules of the master-data DTOs (no database needed). */
class MasterDataValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static PartnerDtos.CreatePartnerRequest partner(String code, String gst, String phone, String email, Integer lead) {
        return new PartnerDtos.CreatePartnerRequest(code, "Sundaram Wires", "A. Person", phone, email, "Chennai", gst, "30 days", lead);
    }

    private static Set<String> invalidFields(Object request) {
        return VALIDATOR.validate(request).stream().map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    @Test
    void aWellFormedPartnerIsAccepted() {
        assertThat(invalidFields(partner("SUP-001", "27AAPFU0939F1ZV", "+91 (44) 2345-6789", "buyer@example.com", 14))).isEmpty();
        assertThat(invalidFields(partner("S1", null, null, null, null))).isEmpty(); // everything but code and name is optional
    }

    @Test
    void gstNumbersMustMatchTheFifteenCharacterFormat() {
        for (String good : new String[] { "27AAPFU0939F1ZV", "29GGGGG1314R9Z6", "07ABCDE1234F1Z5" }) {
            assertThat(invalidFields(partner("SUP-1", good, null, null, null))).as(good).isEmpty();
        }
        for (String bad : new String[] { "27aapfu0939f1zv", "27AAPFU0939F1XV", "27AAPFU0939F1Z", "27AAPFU0939F1ZVV", "AAAAAAAAAAAAAAA",
                "27AAPF10939F1ZV", "2 AAPFU0939F1ZV" }) {
            assertThat(invalidFields(partner("SUP-1", bad, null, null, null))).as(bad).containsExactly("gstNumber");
        }
    }

    @Test
    void codesAreUpperCaseAndBounded() {
        for (String bad : new String[] { "sup-001", "S", "-SUP", "SUP 001", "A".repeat(21), "SUP/001" }) {
            assertThat(invalidFields(partner(bad, null, null, null, null))).as(bad).containsExactly("code");
        }
    }

    @Test
    void phoneEmailAndLeadTimeAreChecked() {
        assertThat(invalidFields(partner("SUP-1", null, "abc", null, null))).containsExactly("phone");
        assertThat(invalidFields(partner("SUP-1", null, null, "not-an-email", null))).containsExactly("email");
        assertThat(invalidFields(partner("SUP-1", null, null, null, -1))).containsExactly("leadTimeDays");
        assertThat(invalidFields(new PartnerDtos.UpdatePartnerRequest("n", null, null, null, null, null, null, null, null))).containsExactly("version");
    }

    private static MaterialDtos.CreateMaterialRequest material(String code, BigDecimal diameter, BigDecimal min, BigDecimal cost) {
        return new MaterialDtos.CreateMaterialRequest(code, "SS304 wire 2.5 mm", MaterialType.STAINLESS, "SS304", diameter, "KG", null,
                min, new BigDecimal("200"), new BigDecimal("1000"), cost, 365, null);
    }

    @Test
    void materialQuantitiesHaveThreeDecimalsAndMoneyFour() {
        assertThat(invalidFields(material("RM-SS-001", new BigDecimal("2.500"), new BigDecimal("100.125"), new BigDecimal("125.1234")))).isEmpty();
        assertThat(invalidFields(material("RM-SS-001", new BigDecimal("2.5001"), BigDecimal.ONE, BigDecimal.ONE))).containsExactly("diameterMm");
        assertThat(invalidFields(material("RM-SS-001", BigDecimal.ONE, new BigDecimal("1.0001"), BigDecimal.ONE))).containsExactly("minStock");
        assertThat(invalidFields(material("RM-SS-001", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1.12345")))).containsExactly("standardCost");
        assertThat(invalidFields(material("RM-SS-001", BigDecimal.ONE, new BigDecimal("-1"), BigDecimal.ONE))).containsExactly("minStock");
        assertThat(invalidFields(material("RM-SS-001", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("-0.0001")))).containsExactly("standardCost");
    }

    @Test
    void materialCodesAndRequiredFieldsAreChecked() {
        assertThat(invalidFields(material("rm-ss-001", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))).containsExactly("code");
        var missing = new MaterialDtos.CreateMaterialRequest("RM-1", " ", null, null, null, "", null, null, null, null, null, 0, null);
        assertThat(invalidFields(missing)).containsExactlyInAnyOrder("name", "materialType", "uom", "shelfLifeDays");
    }
}
