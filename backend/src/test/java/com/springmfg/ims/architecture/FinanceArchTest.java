package com.springmfg.ims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Finance-specific architecture rules (CLAUDE.md rules 13–15).
 */
@AnalyzeClasses(packages = "com.springmfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class FinanceArchTest {

    /**
     * AccountingBridgeService may only call JournalService for GL writes (CLAUDE.md rule 15).
     * It may also depend on inventory events, common infrastructure, and standard Java/Spring.
     */
    @ArchTest
    static final ArchRule accountingBridgeMayOnlyCallJournalService =
            noClasses().that().haveSimpleName("AccountingBridgeService")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..finance.ap..",
                            "..finance.ar..",
                            "..finance.bank..",
                            "..finance.config..",
                            "..finance.dashboard.."
                    )
                    .allowEmptyShould(true)
                    .because("AccountingBridgeService must only call JournalService for GL operations");

    /**
     * JournalService must not call any inventory service or repository (CLAUDE.md rule 15).
     * Stock changes flow only through InventoryPostingService.
     */
    @ArchTest
    static final ArchRule journalServiceMayNotCallInventory =
            noClasses().that().haveSimpleName("JournalService")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..inventory.service..")
                    .allowEmptyShould(true)
                    .because("JournalService must not trigger inventory operations");
}
