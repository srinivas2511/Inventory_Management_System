package com.mfg.ims.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mfg.ims.common.document.DocumentLinkTarget;
import com.mfg.ims.document.service.DocumentLinkTargets;

class DocumentLinkTargetsTest {

    private static DocumentLinkTarget target(String type, Long existingId, String... authorities) {
        return new DocumentLinkTarget() {
            @Override
            public String entityType() {
                return type;
            }

            @Override
            public boolean exists(Long id) {
                return id.equals(existingId);
            }

            @Override
            public Set<String> readAuthorities() {
                return Set.of(authorities);
            }
        };
    }

    @Test
    void collectsTheTypesAndTheirReadAuthoritiesFromWhateverModulesRegister() {
        DocumentLinkTargets targets = new DocumentLinkTargets(List.of(
                target("PRODUCT", 5L, "PRODUCT_VIEW"),
                target("SUPPLIER", 9L, "SUPPLIER_MANAGE", "MASTERDATA_MANAGE")));

        assertThat(targets.types()).containsExactlyInAnyOrder("PRODUCT", "SUPPLIER");
        assertThat(targets.readAuthorities()).containsEntry("PRODUCT", Set.of("PRODUCT_VIEW"))
                .containsEntry("SUPPLIER", Set.of("SUPPLIER_MANAGE", "MASTERDATA_MANAGE"));
    }

    @Test
    void existenceIsDecidedByTheOwningTargetAndNeverThrowsForBadInput() {
        DocumentLinkTargets targets = new DocumentLinkTargets(List.of(target("PRODUCT", 5L, "PRODUCT_VIEW")));

        assertThat(targets.exists("PRODUCT", 5L)).isTrue();
        assertThat(targets.exists("PRODUCT", 6L)).isFalse();
        assertThat(targets.exists("WAREHOUSE", 5L)).as("an unregistered type").isFalse();
        assertThat(targets.exists(null, 5L)).isFalse();
        assertThat(targets.exists("PRODUCT", null)).isFalse();
    }

    @Test
    void twoModulesCannotClaimTheSameType() {
        assertThatThrownBy(() -> new DocumentLinkTargets(List.of(
                target("PRODUCT", 1L, "A"), target("PRODUCT", 2L, "B"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("PRODUCT");
    }

    @Test
    void withNothingRegisteredNothingCanBeLinked() {
        DocumentLinkTargets targets = new DocumentLinkTargets(List.of());
        assertThat(targets.types()).isEmpty();
        assertThat(targets.exists("PRODUCT", 1L)).isFalse();
    }
}
