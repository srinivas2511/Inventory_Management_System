package com.mfg.ims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Advanced manufacturing rules (R18): OEE and scheduling only read; maintenance moves stock and money only through their services. */
@AnalyzeClasses(packages = "com.mfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class AdvancedMfgArchTest {

    @ArchTest
    static final ArchRule oeeAndSchedulingDoNotTouchStock =
            noClasses().that().resideInAnyPackage("com.mfg.ims.oee..", "com.mfg.ims.scheduling..", "com.mfg.ims.prodcalendar..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..inventory.service..", "..inventory.repository..", "..inventory.domain..")
                    .allowEmptyShould(true)
                    .because("OEE and scheduling are planning and reporting; only InventoryPostingService writes stock");

    @ArchTest
    static final ArchRule oeeAndSchedulingDoNotPostJournals =
            noClasses().that().resideInAnyPackage("com.mfg.ims.oee..", "com.mfg.ims.scheduling..", "com.mfg.ims.prodcalendar..")
                    .should().dependOnClassesThat().resideInAPackage("..finance..")
                    .allowEmptyShould(true)
                    .because("planning and OEE create no accounting entries");

    /** People data reaches scheduling through SQL, so HR stays unaware of production planning. */
    @ArchTest
    static final ArchRule oeeAndSchedulingDoNotUseHrClasses =
            noClasses().that().resideInAnyPackage("com.mfg.ims.oee..", "com.mfg.ims.scheduling..", "com.mfg.ims.prodcalendar..")
                    .should().dependOnClassesThat().resideInAPackage("..hrm..")
                    .allowEmptyShould(true)
                    .because("HR depends on production events, never the reverse; skills and attendance are read with SQL");

    @ArchTest
    static final ArchRule nothingDependsOnOeeOrScheduling =
            noClasses().that().resideOutsideOfPackages("com.mfg.ims.oee..", "com.mfg.ims.scheduling..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.mfg.ims.oee..", "com.mfg.ims.scheduling..")
                    .allowEmptyShould(true)
                    .because("production and maintenance stay unaware of the planning and analytics built on them");

    /** The scheduling core is pure: no Spring, no persistence, no other feature packages. */
    @ArchTest
    static final ArchRule schedulingEngineIsPure =
            classes().that().resideInAPackage("com.mfg.ims.scheduling.engine..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("com.mfg.ims.scheduling.engine..", "java..")
                    .allowEmptyShould(true)
                    .because("the engine must stay deterministic and unit-testable without a database");

    /** The OEE arithmetic is pure too. */
    @ArchTest
    static final ArchRule oeeCalculatorIsPure =
            classes().that().haveSimpleName("OeeCalculator")
                    .should().onlyDependOnClassesThat().resideInAnyPackage("com.mfg.ims.oee..", "java..")
                    .allowEmptyShould(true)
                    .because("OEE figures must be checkable by hand without a database");

    @ArchTest
    static final ArchRule maintenanceMovesStockOnlyThroughTheInventoryServices =
            noClasses().that().resideInAPackage("..maintenance..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..inventory.repository..", "..inventory.domain..")
                    .allowEmptyShould(true)
                    .because("spare parts leave stock only through InventoryPostingService (rule 1)");

    @ArchTest
    static final ArchRule maintenanceWritesGlOnlyThroughJournalService =
            noClasses().that().resideInAPackage("..maintenance..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..finance.gl.domain..", "..finance.gl.repository..")
                    .allowEmptyShould(true)
                    .because("journal_headers/journal_lines are written by JournalService only (rule 15)");
}
