package com.mfg.ims.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.mfg.ims.engineering.bom.Bom;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** PLM architecture rules (R15): change control never moves stock and never bypasses the BOM state machine. */
@AnalyzeClasses(packages = "com.mfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class PlmArchTest {

    /** CLAUDE.md rule 1: PLM never reads or writes the ledger and never posts stock. */
    @ArchTest
    static final ArchRule plmDoesNotTouchStock =
            noClasses().that().resideInAPackage("..plm..")
                    .should().dependOnClassesThat(simpleName("InventoryBalance")
                            .or(simpleName("InventoryTransaction"))
                            .or(simpleName("InventoryBalanceRepository"))
                            .or(simpleName("InventoryTransactionRepository"))
                            .or(simpleName("InventoryPostingService")))
                    .allowEmptyShould(true)
                    .because("stock is written only by InventoryPostingService, and PLM is engineering data");

    /** CLAUDE.md rule 9: BOM status changes only through BomService's state machine, never a free-form update. */
    @ArchTest
    static final ArchRule plmNeverSetsBomStatusDirectly =
            noClasses().that().resideInAPackage("..plm..")
                    .should().callMethod(Bom.class, "setStatus", String.class)
                    .allowEmptyShould(true)
                    .because("an ECO creates DRAFT revisions via BomService.copyRevision; activation supersedes the old BOM");

    /** PLM drives BOM revisions only through BomService; it calls no other module's service. */
    @ArchTest
    static final ArchRule plmCallsNoOtherModuleServiceExceptBomService =
            noClasses().that().resideInAPackage("..plm..")
                    .should().dependOnClassesThat(describe("services of other modules (besides BomService, auth principal, audit, common)",
                            c -> c.getPackageName().endsWith(".service")
                                    && !c.getPackageName().contains(".plm.")
                                    && !c.getPackageName().contains(".audit.")
                                    && !c.getPackageName().contains(".auth.")
                                    && !c.getPackageName().contains(".common.")
                                    && !c.getName().equals("com.mfg.ims.engineering.bom.BomService")))
                    .allowEmptyShould(true)
                    .because("cross-module workflows go through the one service that owns the aggregate");

    /** Other modules stay unaware of PLM: it depends on engineering data, never the reverse. */
    @ArchTest
    static final ArchRule nothingOutsidePlmDependsOnPlm =
            noClasses().that().resideOutsideOfPackage("..plm..")
                    .should().dependOnClassesThat().resideInAPackage("..plm..")
                    .allowEmptyShould(true)
                    .because("PLM sits on top of products and BOMs, which must not import it");
}
