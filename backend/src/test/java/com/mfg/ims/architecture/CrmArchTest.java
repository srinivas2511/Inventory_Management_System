package com.mfg.ims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** CRM architecture rules (R12): CRM layers on the customer master and sales; it never touches stock, the GL or production. */
@AnalyzeClasses(packages = "com.mfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class CrmArchTest {

    /** CLAUDE.md rule 1: CRM never touches stock. */
    @ArchTest
    static final ArchRule crmDoesNotTouchStock =
            noClasses().that().resideInAPackage("..crm..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..inventory..", "..warehouse..", "..production..")
                    .allowEmptyShould(true)
                    .because("only InventoryPostingService writes stock; CRM is commercial data only");

    /** CLAUDE.md rule 15: no direct journal writes outside the accounting bridge. */
    @ArchTest
    static final ArchRule crmDoesNotPostJournals =
            noClasses().that().resideInAPackage("..crm..")
                    .should().dependOnClassesThat().resideInAPackage("..finance..")
                    .allowEmptyShould(true)
                    .because("CRM creates no accounting entries");

    /** Sales documents are created only through SalesOrderService, never through its entities or repositories. */
    @ArchTest
    static final ArchRule crmCreatesSalesOrdersOnlyThroughTheService =
            noClasses().that().resideInAPackage("..crm..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..sales.domain..", "..sales.repository..")
                    .allowEmptyShould(true)
                    .because("quotation conversion must reuse SalesOrderService's numbering, validation and audit");

    /** The rest of the system stays unaware of CRM; conversion calls out, never in. */
    @ArchTest
    static final ArchRule nothingDependsOnCrm =
            noClasses().that().resideOutsideOfPackage("..crm..")
                    .should().dependOnClassesThat().resideInAPackage("..crm..")
                    .allowEmptyShould(true)
                    .because("sales and partner modules must not call CRM");
}
