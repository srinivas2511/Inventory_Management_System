package com.springmfg.ims.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Analytics and integration (R19) only read business data and never move stock or post journals. */
@AnalyzeClasses(packages = "com.springmfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class IntegrationArchTest {

    private static final String[] PACKAGES = {"com.springmfg.ims.analytics..", "com.springmfg.ims.integration.."};

    @ArchTest
    static final ArchRule analyticsAndIntegrationDoNotTouchStock =
            noClasses().that().resideInAnyPackage(PACKAGES)
                    .should().dependOnClassesThat().resideInAnyPackage("..inventory.service..", "..inventory.repository..")
                    .allowEmptyShould(true)
                    .because("they read the ledger with SQL; only InventoryPostingService writes stock (they may only"
                            + " consume the StockPostedEvent and its transaction)");

    @ArchTest
    static final ArchRule analyticsAndIntegrationDoNotPostJournals =
            noClasses().that().resideInAnyPackage(PACKAGES)
                    .should().dependOnClassesThat().resideInAnyPackage("..finance.gl.service..", "..finance.gl.repository..")
                    .allowEmptyShould(true)
                    .because("the GL is read with SQL; only the finance services create journal entries");

    @ArchTest
    static final ArchRule webhookSecretsAreOnlyHandledByTheWebhookPackage =
            classes().that().haveSimpleName("SecretCipher")
                    .should().onlyBeAccessed().byClassesThat().resideInAPackage("..integration.webhook..")
                    .allowEmptyShould(true)
                    .because("the encryption key and plaintext secrets must stay inside the webhook package");

    @ArchTest
    static final ArchRule outboundHttpIsOnlyUsedByDelivery =
            classes().that().haveSimpleName("WebhookHttpClient")
                    .should().onlyBeAccessed().byClassesThat().resideInAPackage("..integration.webhook..")
                    .allowEmptyShould(true)
                    .because("every outbound webhook call goes through the delivery service and its address check");

    @ArchTest
    static final ArchRule businessCodeDoesNotDependOnAnalyticsOrIntegration =
            noClasses().that().resideOutsideOfPackages("com.springmfg.ims.analytics..", "com.springmfg.ims.integration..",
                            "com.springmfg.ims.insight..")
                    .should().dependOnClassesThat().resideInAnyPackage(PACKAGES)
                    .allowEmptyShould(true)
                    .because("business modules publish events; they never call into analytics or integration");
}
