package com.springmfg.ims.wms.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.inventory.service.StockLocationQueryService;
import com.springmfg.ims.wms.domain.TransferOrder;
import com.springmfg.ims.wms.domain.TransferOrderLine;
import com.springmfg.ims.wms.domain.WarehouseBin;
import com.springmfg.ims.wms.domain.WmsEnums.TransferLineStatus;
import com.springmfg.ims.wms.domain.WmsEnums.TransferOrderStatus;
import com.springmfg.ims.wms.dto.WmsDtos.TransferLineDto;
import com.springmfg.ims.wms.dto.WmsDtos.TransferOrderDto;
import com.springmfg.ims.wms.repository.TransferOrderLineRepository;
import com.springmfg.ims.wms.repository.WarehouseBinRepository;

/** Turns transfer orders into DTOs, resolving item, batch and bin labels. */
@Component
class TransferOrderMapper {

    private final TransferOrderLineRepository lines;
    private final WarehouseBinRepository bins;
    private final StockLocationQueryService stock;

    TransferOrderMapper(TransferOrderLineRepository lines, WarehouseBinRepository bins,
                        StockLocationQueryService stock) {
        this.lines = lines;
        this.bins = bins;
        this.stock = stock;
    }

    @Transactional(readOnly = true)
    TransferOrderDto toDto(TransferOrder to, boolean withLines) {
        List<TransferOrderLine> ls = lines.findByTransferOrderIdOrderByLineNo(to.getId());
        List<TransferLineDto> lineDtos = withLines ? lineDtos(ls) : List.of();
        return new TransferOrderDto(to.getId(), to.getToNumber(), to.getWarehouseId(), to.getMovementType(),
                to.getSourceDocumentType(), to.getSourceDocumentId(), to.getCrossDockTargetType(),
                to.getCrossDockTargetId(), to.getWaveId(), to.getStatus(), to.getAssignedTo(), to.getCompletedAt(),
                to.getNotes(), to.getCreatedAt(), to.getVersion(), lineDtos, allowedActions(to, ls));
    }

    List<TransferLineDto> lineDtos(List<TransferOrderLine> ls) {
        Set<Long> binIds = new HashSet<>();
        for (TransferOrderLine l : ls) {
            if (l.getFromBinId() != null) binIds.add(l.getFromBinId());
            if (l.getToBinId() != null) binIds.add(l.getToBinId());
        }
        Map<Long, String> binCodes = new HashMap<>();
        for (WarehouseBin b : bins.findByIdIn(binIds)) {
            binCodes.put(b.getId(), b.getBinCode());
        }
        return ls.stream().map(l -> {
            Long itemId = "MATERIAL".equals(l.getItemType()) ? l.getMaterialId() : l.getProductId();
            var info = stock.itemInfo(l.getItemType(), itemId);
            return new TransferLineDto(l.getId(), l.getLineNo(), l.getItemType(), itemId,
                    info.map(i -> i.code()).orElse(null), info.map(i -> i.name()).orElse(null),
                    info.map(i -> i.uom()).orElse(null), l.getBatchId(),
                    stock.batchNumber(l.getItemType(), l.getBatchId()).orElse(null), l.getStockStatus(),
                    l.getFromBinId(), binCodes.get(l.getFromBinId()), l.getToBinId(), binCodes.get(l.getToBinId()),
                    l.getFromLocationId(), l.getToLocationId(), l.getPlannedQty(), l.getConfirmedQty(), l.getStatus(),
                    l.getWarning(), l.getConfirmedAt());
        }).toList();
    }

    static List<String> allowedActions(TransferOrder to, List<TransferOrderLine> ls) {
        if (!to.getStatus().isOpen()) {
            return List.of();
        }
        boolean anyConfirmed = ls.stream().anyMatch(l -> l.getConfirmedQty().signum() > 0);
        boolean anyOpen = ls.stream().anyMatch(l -> l.getStatus() == TransferLineStatus.OPEN
                || l.getStatus() == TransferLineStatus.PARTIAL);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        out.add("ASSIGN");
        if (to.getStatus() == TransferOrderStatus.CREATED) out.add("START");
        if (anyOpen) {
            out.add("SET_TARGET");
            out.add("CONFIRM");
        }
        if (!anyConfirmed) out.add("CANCEL");
        return out;
    }
}
