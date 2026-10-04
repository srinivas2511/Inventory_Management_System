package com.springmfg.ims.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.springmfg.ims.quality.domain.QualityInspection;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Advanced Quality architecture rules (R16): quality records never move stock and never set inspection status directly. */
@AnalyzeClasses(packages = "com.springmfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class QualityPlusArchTest {

    /** CLAUDE.md rule 1: SPC, CAPA, complaints and FAI never read or write the ledger and never post stock. */
    @ArchTest
    static final ArchRule qualityPlusDoesNotTouchStock =
            noClasses().that().resideInAPackage("..qualityplus..")
                    .should().dependOnClassesThat(simpleName("InventoryBalance")
                            .or(simpleName("InventoryTransaction"))
                            .or(simpleName("InventoryBalanceRepository"))
                            .or(simpleName("InventoryTransactionRepository"))
                            .or(simpleName("InventoryPostingService")))
                    .allowEmptyShould(true)
                    .because("stock is written only by InventoryPostingService; these records are quality data");

    /** CLAUDE.md rule 9: an FAI inspection is closed through InspectionService, never by a free-form status update. */
    @ArchTest
    static final ArchRule qualityPlusNeverSetsInspectionStatusDirectly =
            noClasses().that().resideInAPackage("..qualityplus..")
                    .should().callMethod(QualityInspection.class, "setStatus", String.class)
                    .allowEmptyShould(true)
                    .because("first article inspections are completed by InspectionService.closeFirstArticle");

    /** Quality plus never writes the accounting journals (rule 15); it has no finance dependency at all. */
    @ArchTest
    static final ArchRule qualityPlusDoesNotDependOnFinance =
            noClasses().that().resideInAPackage("..qualityplus..")
                    .should().dependOnClassesThat().resideInAPackage("..finance..")
                    .allowEmptyShould(true)
                    .because("journal entries come only from AccountingBridgeService and the finance services");

    /** Other modules stay unaware of the quality plus module; they talk to it through common events. */
    @ArchTest
    static final ArchRule nothingOutsideQualityPlusDependsOnQualityPlus =
            noClasses().that().resideOutsideOfPackage("..qualityplus..")
                    .should().dependOnClassesThat().resideInAPackage("..qualityplus..")
                    .allowEmptyShould(true)
                    .because("quality and PLM publish common events instead of calling into SPC, CAPA or FAI");
}
