package com.mfg.ims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** MRP architecture rules (R11, CLAUDE.md rules 1 and 20): planning is advisory and never touches stock or the GL. */
@AnalyzeClasses(packages = "com.mfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class MrpArchTest {

    /** MRP reads stock through SQL only; it must not use the posting service or the ledger/balance classes. */
    @ArchTest
    static final ArchRule mrpDoesNotWriteStock =
            noClasses().that().resideInAPackage("..mrp..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..inventory.service..", "..inventory.repository..", "..inventory.domain..")
                    .allowEmptyShould(true)
                    .because("only InventoryPostingService writes stock; MRP is advisory");

    @ArchTest
    static final ArchRule mrpDoesNotPostJournals =
            noClasses().that().resideInAPackage("..mrp..")
                    .should().dependOnClassesThat().resideInAPackage("..finance..")
                    .allowEmptyShould(true)
                    .because("MRP creates no accounting entries");

    /** The rest of the system stays unaware of MRP; conversion calls out, never in. */
    @ArchTest
    static final ArchRule nothingDependsOnMrp =
            noClasses().that().resideOutsideOfPackage("..mrp..")
                    .should().dependOnClassesThat().resideInAPackage("..mrp..")
                    .allowEmptyShould(true)
                    .because("procurement and production must not call MRP");

    /** The calculation core is pure: no Spring, no persistence, no other feature packages. */
    @ArchTest
    static final ArchRule engineIsPure =
            classes().that().resideInAPackage("..mrp.engine..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("..mrp.engine..", "java..")
                    .allowEmptyShould(true)
                    .because("the engine must stay deterministic and unit-testable without a database");
}
