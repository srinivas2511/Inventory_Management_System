package com.mfg.ims.integration.tally;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TallyExportServiceTest {

    @Test
    void xmlSpecialCharactersAreEscaped() {
        assertThat(TallyExportService.escape("R&D <ops> \"Q\" it's"))
                .isEqualTo("R&amp;D &lt;ops&gt; &quot;Q&quot; it&apos;s");
        assertThat(TallyExportService.escape(null)).isEmpty();
    }

    @Test
    void charactersXmlCannotCarryAreDropped() {
        assertThat(TallyExportService.escape("a\u0000b\u0008c\u000Bd￾e")).isEqualTo("abcde");
        assertThat(TallyExportService.escape("tab\there\nnewline")).isEqualTo("tab\there\nnewline");
        assertThat(TallyExportService.escape("rupee ₹ 😀")).isEqualTo("rupee ₹ 😀");
    }

    @Test
    void ledgerNamesCarryTheAccountCodeSoVouchersAndMastersAgree() {
        assertThat(TallyExportService.ledgerName("1101", "Accounts Receivable Control"))
                .isEqualTo("1101 Accounts Receivable Control");
    }

    @Test
    void everyAccountTypeMapsToATallyGroup() {
        assertThat(TallyExportService.tallyGroup("ASSET")).isEqualTo("Current Assets");
        assertThat(TallyExportService.tallyGroup("LIABILITY")).isEqualTo("Current Liabilities");
        assertThat(TallyExportService.tallyGroup("EQUITY")).isEqualTo("Capital Account");
        assertThat(TallyExportService.tallyGroup("REVENUE")).isEqualTo("Sales Accounts");
        assertThat(TallyExportService.tallyGroup("COGS")).isEqualTo("Direct Expenses");
        assertThat(TallyExportService.tallyGroup("EXPENSE")).isEqualTo("Indirect Expenses");
    }
}
