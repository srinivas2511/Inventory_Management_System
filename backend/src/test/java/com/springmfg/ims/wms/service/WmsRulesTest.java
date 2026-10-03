package com.springmfg.ims.wms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.inventory.service.StockLocationQueryService.ItemInfo;
import com.springmfg.ims.wms.domain.WarehouseBin;
import com.springmfg.ims.wms.domain.WmsEnums.BinType;
import com.springmfg.ims.wms.domain.WmsEnums.CycleCountStatus;
import com.springmfg.ims.wms.domain.WmsEnums.MovementType;
import com.springmfg.ims.wms.domain.WmsEnums.TransferOrderStatus;
import com.springmfg.ims.wms.domain.WmsEnums.WaveStatus;

/** Business rules of the warehouse module that need no database: state machines, capacity and ABC classes. */
class WmsRulesTest {

    // ---- state machines -------------------------------------------------------------------------------------

    @Test
    void transferOrderStates_moveForwardOnly() {
        assertThat(TransferOrderStatus.CREATED.canMoveTo(TransferOrderStatus.IN_PROGRESS)).isTrue();
        assertThat(TransferOrderStatus.CREATED.canMoveTo(TransferOrderStatus.COMPLETED)).isTrue();
        assertThat(TransferOrderStatus.IN_PROGRESS.canMoveTo(TransferOrderStatus.COMPLETED)).isTrue();
        assertThat(TransferOrderStatus.IN_PROGRESS.canMoveTo(TransferOrderStatus.CREATED)).isFalse();
        for (TransferOrderStatus done : List.of(TransferOrderStatus.COMPLETED, TransferOrderStatus.CANCELLED)) {
            for (TransferOrderStatus next : TransferOrderStatus.values()) {
                assertThat(done.canMoveTo(next)).as("%s -> %s", done, next).isFalse();
            }
            assertThat(done.isOpen()).isFalse();
        }
        assertThat(TransferOrderStatus.CREATED.isOpen()).isTrue();
    }

    @Test
    void waveStates_releaseBeforeCompletion() {
        assertThat(WaveStatus.CREATED.canMoveTo(WaveStatus.RELEASED)).isTrue();
        assertThat(WaveStatus.CREATED.canMoveTo(WaveStatus.COMPLETED)).isFalse();
        assertThat(WaveStatus.RELEASED.canMoveTo(WaveStatus.COMPLETED)).isTrue();
        assertThat(WaveStatus.RELEASED.canMoveTo(WaveStatus.CREATED)).isFalse();
        assertThat(WaveStatus.COMPLETED.canMoveTo(WaveStatus.CANCELLED)).isFalse();
    }

    @Test
    void cycleCountStates_reviewBeforePosting_andRecountIsAllowed() {
        assertThat(CycleCountStatus.CREATED.canMoveTo(CycleCountStatus.POSTED)).isFalse();
        assertThat(CycleCountStatus.IN_PROGRESS.canMoveTo(CycleCountStatus.REVIEW)).isTrue();
        assertThat(CycleCountStatus.REVIEW.canMoveTo(CycleCountStatus.IN_PROGRESS)).as("recount").isTrue();
        assertThat(CycleCountStatus.REVIEW.canMoveTo(CycleCountStatus.POSTED)).isTrue();
        assertThat(CycleCountStatus.POSTED.canMoveTo(CycleCountStatus.CANCELLED)).isFalse();
        assertThat(CycleCountStatus.CREATED.isCountable()).isTrue();
        assertThat(CycleCountStatus.REVIEW.isCountable()).isFalse();
    }

    // ---- movement authority -----------------------------------------------------------------------------------

    private static void login(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", "n",
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void eachMovementTypeNeedsItsOwnPermission() {
        try {
            login("PICKING_MANAGE");
            TransferOrderService.requireMovementAuthority(MovementType.PICKING);
            assertThatThrownBy(() -> TransferOrderService.requireMovementAuthority(MovementType.PUTAWAY))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> TransferOrderService.requireMovementAuthority(MovementType.CROSS_DOCK))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> TransferOrderService.requireMovementAuthority(MovementType.INTERNAL_TRANSFER))
                    .isInstanceOf(AccessDeniedException.class);

            login("PUTAWAY_MANAGE");
            TransferOrderService.requireMovementAuthority(MovementType.PUTAWAY);
            TransferOrderService.requireMovementAuthority(MovementType.CROSS_DOCK);

            login("TRANSFER_MANAGE");
            TransferOrderService.requireMovementAuthority(MovementType.INTERNAL_TRANSFER);

            SecurityContextHolder.clearContext();
            assertThatThrownBy(() -> TransferOrderService.requireMovementAuthority(MovementType.PICKING))
                    .as("no login, no permission").isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ---- bin capacity (decision #15) --------------------------------------------------------------------------

    private StockLocationQueryService stock;
    private SystemSettingService settings;
    private BinCapacityService capacity;

    @BeforeEach
    void capacityFixture() {
        stock = mock(StockLocationQueryService.class);
        settings = mock(SystemSettingService.class);
        capacity = new BinCapacityService(stock, settings);
        when(stock.itemInfo(any(), any())).thenReturn(Optional.of(new ItemInfo("M1", "Wire", "KG", "SPRING_STEEL")));
        when(stock.unitWeightKg(any(), any())).thenReturn(BigDecimal.ONE);
        when(stock.locationWeightKg(any())).thenReturn(new BigDecimal("80"));
    }

    private static WarehouseBin bin(BigDecimal maxKg, String allowed, BinType type) {
        WarehouseBin b = new WarehouseBin();
        b.setBinCode("A-01");
        b.setLocationId(7L);
        b.setMaxWeightKg(maxKg);
        b.setAllowedMaterialTypes(allowed);
        b.setBinType(type);
        return b;
    }

    @Test
    void weightBreach_warns_whenAdvisory() {
        when(settings.getBoolean("wms.bin_capacity.enforce", false)).thenReturn(false);
        WarehouseBin b = bin(new BigDecimal("100"), null, BinType.STANDARD);

        assertThat(capacity.check(b, "MATERIAL", 1L, new BigDecimal("20"))).isEmpty();
        List<String> warnings = capacity.checkAndEnforce(b, "MATERIAL", 1L, new BigDecimal("30"));

        assertThat(warnings).singleElement().asString().contains("110").contains("100 kg");
    }

    @Test
    void weightBreach_isRejected_whenEnforced() {
        when(settings.getBoolean("wms.bin_capacity.enforce", false)).thenReturn(true);
        WarehouseBin b = bin(new BigDecimal("100"), null, BinType.STANDARD);

        assertThat(capacity.checkAndEnforce(b, "MATERIAL", 1L, new BigDecimal("20"))).isEmpty();
        assertThatThrownBy(() -> capacity.checkAndEnforce(b, "MATERIAL", 1L, new BigDecimal("30")))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).getCode()).isEqualTo(ErrorCode.BIN_CAPACITY_EXCEEDED));
    }

    @Test
    void allowedMaterialTypes_areCheckedCaseInsensitively() {
        WarehouseBin b = bin(null, "stainless, spring_steel", BinType.STANDARD);
        assertThat(capacity.check(b, "MATERIAL", 1L, BigDecimal.TEN)).isEmpty();

        WarehouseBin other = bin(null, "STAINLESS", BinType.STANDARD);
        assertThat(capacity.check(other, "MATERIAL", 1L, BigDecimal.TEN)).singleElement().asString()
                .contains("does not allow");
        assertThat(capacity.check(other, "PRODUCT", 2L, BigDecimal.TEN)).as("finished goods need PRODUCT").hasSize(1);
        assertThat(capacity.check(bin(null, "PRODUCT", BinType.STANDARD), "PRODUCT", 2L, BigDecimal.TEN)).isEmpty();
    }

    @Test
    void quarantineAndQcBins_acceptAnything() {
        WarehouseBin quarantine = bin(new BigDecimal("1"), "STAINLESS", BinType.QUARANTINE);
        WarehouseBin qc = bin(new BigDecimal("1"), "STAINLESS", BinType.QC);
        assertThat(capacity.check(quarantine, "MATERIAL", 1L, new BigDecimal("500"))).isEmpty();
        assertThat(capacity.check(qc, "MATERIAL", 1L, new BigDecimal("500"))).isEmpty();
    }

    @Test
    void unlimitedBin_neverWarnsAboutWeight() {
        assertThat(capacity.check(bin(null, null, BinType.STANDARD), "MATERIAL", 1L, new BigDecimal("1000000"))).isEmpty();
    }

    // ---- ABC classification -----------------------------------------------------------------------------------

    @Test
    void abcClasses_followCumulativeConsumptionValue() {
        // values 500, 300, 100, 50, 30, 20 = 1000: cumulative before each item = 0, 50, 80, 90, 95, 98 percent
        Map<String, BigDecimal> value = Map.of("M:1", new BigDecimal("500"), "M:2", new BigDecimal("300"),
                "M:3", new BigDecimal("100"), "M:4", new BigDecimal("50"), "M:5", new BigDecimal("30"),
                "P:6", new BigDecimal("20"));
        List<String> keys = List.of("M:1", "M:2", "M:3", "M:4", "M:5", "P:6", "M:7");

        Map<String, String> classes = CycleCountService.classifyByValue(value, keys, new BigDecimal("80"),
                new BigDecimal("95"));

        assertThat(classes.get("M:1")).isEqualTo("A");
        assertThat(classes.get("M:2")).as("starts at 50%: still A").isEqualTo("A");
        assertThat(classes.get("M:3")).as("starts at exactly 80%: B").isEqualTo("B");
        assertThat(classes.get("M:4")).isEqualTo("B");
        assertThat(classes.get("M:5")).as("starts at exactly 95%: C").isEqualTo("C");
        assertThat(classes.get("P:6")).isEqualTo("C");
        assertThat(classes.get("M:7")).as("no consumption at all").isEqualTo("C");
    }

    @Test
    void abcClasses_withNoConsumption_areAllC() {
        Map<String, String> classes = CycleCountService.classifyByValue(Map.of(), List.of("M:1", "P:2"),
                new BigDecimal("80"), new BigDecimal("95"));
        assertThat(classes.values()).containsOnly("C");
    }

    @Test
    void abcClasses_singleItemIsA() {
        Map<String, String> classes = CycleCountService.classifyByValue(Map.of("M:1", BigDecimal.TEN),
                List.of("M:1"), new BigDecimal("80"), new BigDecimal("95"));
        assertThat(classes.get("M:1")).isEqualTo("A");
    }
}
