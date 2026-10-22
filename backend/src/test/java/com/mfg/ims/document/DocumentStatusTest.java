package com.mfg.ims.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.mfg.ims.document.domain.DocumentStatus;

class DocumentStatusTest {

    private static final Map<DocumentStatus, Set<DocumentStatus>> ALLOWED = Map.of(
            DocumentStatus.DRAFT, Set.of(DocumentStatus.REVIEW, DocumentStatus.OBSOLETE),
            DocumentStatus.REVIEW, Set.of(DocumentStatus.APPROVED, DocumentStatus.DRAFT),
            DocumentStatus.APPROVED, Set.of(DocumentStatus.OBSOLETE),
            DocumentStatus.OBSOLETE, Set.of());

    @Test
    void everyTransitionIsAllowedOrForbiddenExactlyAsDesigned() {
        for (DocumentStatus from : DocumentStatus.values()) {
            for (DocumentStatus to : DocumentStatus.values()) {
                assertThat(from.canMoveTo(to)).as(from + " -> " + to).isEqualTo(ALLOWED.get(from).contains(to));
            }
        }
    }

    @Test
    void obsoleteIsTerminal() {
        for (DocumentStatus to : DocumentStatus.values()) {
            assertThat(DocumentStatus.OBSOLETE.canMoveTo(to)).isFalse();
        }
    }

    @Test
    void approvalIsOnlyReachableThroughReview() {
        for (DocumentStatus from : DocumentStatus.values()) {
            assertThat(from.canMoveTo(DocumentStatus.APPROVED)).isEqualTo(from == DocumentStatus.REVIEW);
        }
    }
}
