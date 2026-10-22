package com.mfg.ims.inventory.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.inventory.dto.OpeningBalanceLine;
import com.mfg.ims.inventory.dto.PostingCommand;
import com.mfg.ims.inventory.dto.PostingResult;

/**
 * Loads opening balances using the posting service with type OPENING_BALANCE.
 */
@Service
public class OpeningBalanceService {

    private final InventoryPostingService postingService;
    private final BatchService batchService;

    public OpeningBalanceService(InventoryPostingService postingService, BatchService batchService) {
        this.postingService = postingService;
        this.batchService = batchService;
    }

    @Transactional
    public List<PostingResult> loadOpeningBalances(List<OpeningBalanceLine> lines) {
        return lines.stream().map(line -> {
            if (line.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED,
                        "Opening balance quantity must be positive for line: " + line);
            }

            // Create a batch for material opening balances if not provided
            Long batchId = line.batchId();
            if ("MATERIAL".equals(line.itemType()) && batchId == null) {
                var batch = batchService.createMaterialBatch(
                        line.materialId(), null, null, null,
                        null, java.time.LocalDate.now(), null,
                        line.quantity(), null, null, line.unitCost(), "AVAILABLE");
                batchId = batch.getId();
            }

            PostingCommand cmd = new PostingCommand(
                    "OPENING_BALANCE",
                    line.itemType(), line.materialId(), line.productId(),
                    "MATERIAL".equals(line.itemType()) ? batchId : null,
                    "PRODUCT".equals(line.itemType()) ? batchId : null,
                    line.warehouseId(), line.locationId(), line.stockStatus(),
                    line.quantity(), line.uom(), line.unitCost(),
                    "OPENING_BALANCE", 0L, "OB", line.remarks());

            return postingService.post(cmd);
        }).toList();
    }
}
