package com.mfg.ims.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** WMS architecture rules (R14): stock moves only through InventoryPostingService, on transfer-order confirmation. */
@AnalyzeClasses(packages = "com.mfg.ims", importOptions = ImportOption.DoNotIncludeTests.class)
class WmsArchTest {

    /** CLAUDE.md rule 1: the warehouse module never reads or writes the ledger tables itself. */
    @ArchTest
    static final ArchRule wmsDoesNotTouchLedgerTables =
            noClasses().that().resideInAPackage("..wms..")
                    .should().dependOnClassesThat(simpleName("InventoryBalance")
                            .or(simpleName("InventoryTransaction"))
                            .or(simpleName("InventoryBalanceRepository"))
                            .or(simpleName("InventoryTransactionRepository")))
                    .allowEmptyShould(true)
                    .because("stock is written by InventoryPostingService and read through StockLocationQueryService");

    /** Only transfer-order confirmation moves stock; planning, picking, put-away and counting never post. */
    @ArchTest
    static final ArchRule onlyTransferOrderServicePosts =
            noClasses().that().resideInAPackage("..wms..")
                    .and().doNotHaveSimpleName("TransferOrderService")
                    .should().dependOnClassesThat(simpleName("InventoryPostingService"))
                    .allowEmptyShould(true)
                    .because("a physical move is posted once, when its transfer order line is confirmed");

    /** A cycle count only raises adjustments that go through the approval workflow; it never changes stock. */
    @ArchTest
    static final ArchRule cycleCountsNeverPost =
            noClasses().that().haveSimpleName("CycleCountService")
                    .should().dependOnClassesThat(simpleName("InventoryPostingService")
                            .or(simpleName("TransferCommand")).or(simpleName("PostingCommand")))
                    .allowEmptyShould(true)
                    .because("count variances go through StockAdjustmentService and its approval steps");

    /** The warehouse module reads procurement, sales and production data, but calls none of their services. */
    @ArchTest
    static final ArchRule wmsUsesOtherModulesThroughRepositoriesAndEventsOnly =
            noClasses().that().resideInAPackage("..wms..")
                    .should().dependOnClassesThat(describe("services or controllers of procurement, sales or production",
                            c -> (c.getPackageName().contains(".procurement.") || c.getPackageName().contains(".sales.")
                                    || c.getPackageName().contains(".production."))
                                    && (c.getPackageName().endsWith(".service") || c.getPackageName().endsWith(".web"))))
                    .allowEmptyShould(true)
                    .because("planning is triggered by events and reads the documents it needs; it never drives their workflows");

    /** Other modules stay unaware of the warehouse module: it listens to them, never the reverse. */
    @ArchTest
    static final ArchRule nothingOutsideWmsDependsOnWms =
            noClasses().that().resideOutsideOfPackage("..wms..")
                    .should().dependOnClassesThat().resideInAPackage("..wms..")
                    .allowEmptyShould(true)
                    .because("WMS depends on procurement and sales events, not the reverse");
}
