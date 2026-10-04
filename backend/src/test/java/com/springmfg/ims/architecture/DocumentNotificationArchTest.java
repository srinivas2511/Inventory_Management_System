package com.springmfg.ims.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Architecture rules for the document module and the notification engine (R17). */
@AnalyzeClasses(packages = "com.springmfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class DocumentNotificationArchTest {

    /** CLAUDE.md rule 1: documents and notifications never read or write the ledger. */
    @ArchTest
    static final ArchRule documentsAndNotificationsDoNotTouchStock =
            noClasses().that().resideInAnyPackage("..document..", "..notification..")
                    .should().dependOnClassesThat(simpleName("InventoryBalance")
                            .or(simpleName("InventoryTransaction"))
                            .or(simpleName("InventoryBalanceRepository"))
                            .or(simpleName("InventoryTransactionRepository"))
                            .or(simpleName("InventoryPostingService")))
                    .allowEmptyShould(true)
                    .because("stock is written only by InventoryPostingService");

    /**
     * The document module knows no feature module: the records it can link to register themselves through
     * {@code DocumentLinkTarget} in {@code common}. (PLM and quality-plus additionally forbid anything outside them
     * from depending on them.)
     */
    @ArchTest
    static final ArchRule documentModuleDependsOnNoFeatureModule =
            noClasses().that().resideInAPackage("..document..")
                    .should().dependOnClassesThat().resideInAnyPackage("..plm..", "..qualityplus..", "..quality..",
                            "..product..", "..partner..", "..masterdata..", "..engineering..", "..inventory..",
                            "..procurement..", "..sales..", "..production..", "..crm..", "..hrm..", "..finance..",
                            "..wms..", "..mrp..", "..controlling..", "..maintenance..", "..insight..")
                    .allowEmptyShould(true)
                    .because("a module owns its own records and offers them to documents, not the other way round");

    /** Content storage is an implementation detail of the document service (an object-store swap must stay local). */
    @ArchTest
    static final ArchRule storageIsOnlyUsedByTheDocumentService =
            noClasses().that().resideOutsideOfPackages("..document.service..", "..document.storage..")
                    .should().dependOnClassesThat().resideInAPackage("..document.storage..")
                    .allowEmptyShould(true)
                    .because("only DocumentService decides what is stored, read or deleted");

    /** SMTP goes through EmailSender; only it and the password-reset mail in AuthService may use JavaMailSender. */
    @ArchTest
    static final ArchRule onlyTheSmtpSenderAndPasswordResetUseJavaMail =
            noClasses().that(describe("are not SmtpEmailSender or AuthService",
                            c -> !c.getSimpleName().equals("SmtpEmailSender") && !c.getSimpleName().equals("AuthService")))
                    .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.mail.javamail.JavaMailSender")
                    .allowEmptyShould(true)
                    .because("e-mail is queued in the outbox and sent by the delivery job, never inline");

    /** Other modules tell the engine about events; they never create notifications themselves. */
    @ArchTest
    static final ArchRule otherModulesDoNotCallTheNotificationService =
            noClasses().that().resideOutsideOfPackage("..notification..")
                    .should().dependOnClassesThat(simpleName("NotificationService")
                            .or(simpleName("NotificationRepository"))
                            .or(simpleName("NotificationDeliveryService")))
                    .allowEmptyShould(true)
                    .because("business code publishes events; the notification module listens, after commit");

    /** The notification module is a listener: nothing in it reaches into another module's service. */
    @ArchTest
    static final ArchRule notificationsDependOnNoOtherModuleService =
            noClasses().that().resideInAPackage("..notification..")
                    .should().dependOnClassesThat(describe("services of other modules",
                            c -> c.getPackageName().endsWith(".service")
                                    && !c.getPackageName().contains(".notification.")
                                    && !c.getPackageName().contains(".audit.")
                                    && !c.getPackageName().contains(".auth.")
                                    && !c.getPackageName().contains(".common.")))
                    .allowEmptyShould(true)
                    .because("it reads what it needs from repositories and the event payload");
}
