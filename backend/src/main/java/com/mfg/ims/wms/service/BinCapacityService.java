package com.mfg.ims.wms.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.inventory.service.StockLocationQueryService;
import com.mfg.ims.wms.domain.WarehouseBin;
import com.mfg.ims.wms.domain.WmsEnums.BinType;

/**
 * Bin capacity rules (WMS-002, decision #15): a breach is advisory by default and only rejects the movement when
 * {@code wms.bin_capacity.enforce} is true. Weight and allowed material types are checked; volume is stored on the
 * bin but not checked because the master data holds no item volumes.
 */
@Service
public class BinCapacityService {

    public static final String ENFORCE_KEY = "wms.bin_capacity.enforce";

    private final StockLocationQueryService stock;
    private final SystemSettingService settings;

    public BinCapacityService(StockLocationQueryService stock, SystemSettingService settings) {
        this.stock = stock;
        this.settings = settings;
    }

    public boolean enforced() {
        return settings.getBoolean(ENFORCE_KEY, false);
    }

    /** Warnings that moving {@code qty} of the item into the bin would raise; empty when it fits. */
    @Transactional(readOnly = true)
    public List<String> check(WarehouseBin bin, String itemType, Long itemId, BigDecimal qty) {
        List<String> warnings = new ArrayList<>();
        if (bin.getBinType() == BinType.QUARANTINE || bin.getBinType() == BinType.QC) {
            return warnings;
        }
        Set<String> allowed = allowedTypes(bin);
        if (!allowed.isEmpty()) {
            boolean ok;
            if ("MATERIAL".equals(itemType)) {
                String type = stock.itemInfo(itemType, itemId).map(StockLocationQueryService.ItemInfo::materialType)
                        .orElse("");
                ok = allowed.contains(type == null ? "" : type.toUpperCase(Locale.ROOT));
            } else {
                ok = allowed.contains("PRODUCT") || allowed.contains("FINISHED_GOODS");
            }
            if (!ok) {
                warnings.add("Bin " + bin.getBinCode() + " does not allow this item type (allowed: "
                        + bin.getAllowedMaterialTypes() + ")");
            }
        }
        if (bin.getMaxWeightKg() != null) {
            BigDecimal incoming = stock.unitWeightKg(itemType, itemId).multiply(qty);
            BigDecimal after = stock.locationWeightKg(bin.getLocationId()).add(incoming);
            if (after.compareTo(bin.getMaxWeightKg()) > 0) {
                warnings.add("Bin " + bin.getBinCode() + " would hold " + after.stripTrailingZeros().toPlainString()
                        + " kg, over its " + bin.getMaxWeightKg().stripTrailingZeros().toPlainString() + " kg capacity");
            }
        }
        return warnings;
    }

    /** Like {@link #check} but rejects the movement when enforcement is switched on. */
    @Transactional(readOnly = true)
    public List<String> checkAndEnforce(WarehouseBin bin, String itemType, Long itemId, BigDecimal qty) {
        List<String> warnings = check(bin, itemType, itemId, qty);
        if (!warnings.isEmpty() && enforced()) {
            throw new BusinessRuleException(ErrorCode.BIN_CAPACITY_EXCEEDED, String.join("; ", warnings));
        }
        return warnings;
    }

    private static Set<String> allowedTypes(WarehouseBin bin) {
        String raw = bin.getAllowedMaterialTypes();
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    }
}
