package com.springmfg.ims.wms.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.springmfg.ims.wms.domain.WmsEnums.BinType;
import com.springmfg.ims.wms.domain.WmsEnums.CycleCountStatus;
import com.springmfg.ims.wms.domain.WmsEnums.CycleCountType;
import com.springmfg.ims.wms.domain.WmsEnums.MovementType;
import com.springmfg.ims.wms.domain.WmsEnums.PutAwayStrategy;
import com.springmfg.ims.wms.domain.WmsEnums.TransferLineStatus;
import com.springmfg.ims.wms.domain.WmsEnums.TransferOrderStatus;
import com.springmfg.ims.wms.domain.WmsEnums.WaveStatus;
import com.springmfg.ims.wms.domain.WmsEnums.ZoneType;

/** Request and response records of the warehouse module. Entities never leave the service layer. */
public final class WmsDtos {

    private WmsDtos() {
    }

    // ---- zones, bins, rules --------------------------------------------------------------------------------

    public record ZoneDto(Long id, Long warehouseId, String zoneCode, String name, ZoneType zoneType, boolean active,
                          long binCount, Long version) {
    }

    public record SaveZoneRequest(@NotNull Long warehouseId, @NotBlank @Size(max = 10) String zoneCode,
                                  @NotBlank @Size(max = 60) String name, @NotNull ZoneType zoneType, Boolean active,
                                  Long version) {
    }

    public record BinDto(Long id, Long zoneId, String zoneCode, ZoneType zoneType, Long warehouseId, Long locationId,
                         String binCode, String aisle, String rack, String level, String binPosition, BinType binType,
                         BigDecimal maxWeightKg, BigDecimal maxVolumeM3, BigDecimal currentWeightKg,
                         String allowedMaterialTypes, boolean active, boolean occupied, Long version) {
    }

    public record SaveBinRequest(@NotNull Long zoneId, @NotBlank @Size(max = 20) String binCode,
                                 @Size(max = 10) String aisle, @Size(max = 10) String rack,
                                 @Size(max = 10) String level, @Size(max = 10) String binPosition, BinType binType,
                                 @DecimalMin(value = "0.001") BigDecimal maxWeightKg,
                                 @DecimalMin(value = "0.0001") BigDecimal maxVolumeM3,
                                 @Size(max = 200) String allowedMaterialTypes, String locationType, Boolean active,
                                 Long version) {
    }

    public record BinStockLineDto(String itemType, Long itemId, String itemCode, String itemName, Long batchId,
                                  String batchNumber, String stockStatus, BigDecimal qtyOnHand,
                                  BigDecimal qtyReserved, String uom) {
    }

    public record BinAssignmentDto(Long id, Long binId, String binCode, Long materialId, Long productId,
                                   String itemCode, String itemName, boolean primary) {
    }

    public record SaveAssignmentRequest(Long materialId, Long productId, Boolean primary) {
    }

    public record PutAwayRuleDto(Long id, Long warehouseId, String materialType, PutAwayStrategy strategy) {
    }

    public record SavePutAwayRuleRequest(@NotNull Long warehouseId, @Size(max = 40) String materialType,
                                         @NotNull PutAwayStrategy strategy) {
    }

    // ---- transfer orders -----------------------------------------------------------------------------------

    public record TransferLineDto(Long id, int lineNo, String itemType, Long itemId, String itemCode, String itemName,
                                  String uom, Long batchId, String batchNumber, String stockStatus, Long fromBinId,
                                  String fromBinCode, Long toBinId, String toBinCode, Long fromLocationId,
                                  Long toLocationId, BigDecimal plannedQty, BigDecimal confirmedQty,
                                  TransferLineStatus status, String warning, Instant confirmedAt) {
    }

    public record TransferOrderDto(Long id, String toNumber, Long warehouseId, MovementType movementType,
                                   String sourceDocumentType, Long sourceDocumentId, String crossDockTargetType,
                                   Long crossDockTargetId, Long waveId, TransferOrderStatus status, Long assignedTo,
                                   Instant completedAt, String notes, Instant createdAt, Long version,
                                   List<TransferLineDto> lines, List<String> allowedActions) {
    }

    public record TransferLineRequest(@NotBlank String itemType, Long materialId, Long productId, Long batchId,
                                      Long fromBinId, Long fromLocationId, @NotNull Long toBinId,
                                      @NotNull @DecimalMin(value = "0.001") BigDecimal quantity) {
    }

    public record CreateTransferRequest(@NotNull Long warehouseId, @Size(max = 500) String notes,
                                        @NotEmpty List<@Valid TransferLineRequest> lines) {
    }

    public record CrossDockRequest(@NotNull Long goodsReceiptId, @NotBlank String targetType, @NotNull Long targetId,
                                   @Size(max = 500) String notes) {
    }

    public record ConfirmLineRequest(@NotNull Long lineId, @NotNull @DecimalMin(value = "0.001") BigDecimal quantity,
                                     String scannedFromBin, String scannedToBin, String scannedBatch,
                                     Long toBinId) {
    }

    public record ConfirmRequest(@NotEmpty List<@Valid ConfirmLineRequest> lines) {
    }

    public record ConfirmResultDto(TransferOrderDto order, List<String> warnings) {
    }

    public record AssignRequest(@NotNull Long userId) {
    }

    public record CancelRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record SetTargetRequest(@NotNull Long lineId, @NotNull Long toBinId) {
    }

    // ---- waves ----------------------------------------------------------------------------------------------

    public record WaveDto(Long id, String waveNumber, Long warehouseId, WaveStatus status, Instant releasedAt,
                          String notes, List<Long> salesOrderIds, List<Long> transferOrderIds, Long version) {
    }

    public record CreateWaveRequest(@NotEmpty List<Long> salesOrderIds, Long warehouseId,
                                    @Size(max = 300) String notes) {
    }

    public record PickOrderQty(Long salesOrderId, String orderNumber, Long transferOrderId, BigDecimal quantity) {
    }

    public record PickListLineDto(Long zoneId, String zoneCode, Long binId, String binCode, Long locationId,
                                  String itemType, Long itemId, String itemCode, String itemName, Long batchId,
                                  String batchNumber, BigDecimal totalQty, List<PickOrderQty> orders) {
    }

    // ---- cycle counts ----------------------------------------------------------------------------------------

    public record CycleCountLineDto(Long id, int lineNo, Long binId, String binCode, Long locationId, String itemType,
                                    Long itemId, String itemCode, String itemName, Long batchId, String batchNumber,
                                    String stockStatus, BigDecimal systemQty, BigDecimal countedQty,
                                    BigDecimal varianceQty, Long adjustmentId) {
    }

    public record CycleCountDto(Long id, String countNumber, Long warehouseId, CycleCountType countType, Long zoneId,
                                String abcClass, Integer sampleSize, CycleCountStatus status, String notes,
                                Instant reviewedAt, Instant postedAt, int lineCount, int countedCount,
                                int varianceLineCount, Long version, List<CycleCountLineDto> lines,
                                List<String> allowedActions) {
    }

    public record CreateCycleCountRequest(@NotNull Long warehouseId, @NotNull CycleCountType countType, Long zoneId,
                                          @Size(max = 1) String abcClass, Integer sampleSize,
                                          @Size(max = 300) String notes) {
    }

    public record CountEntry(@NotNull Long lineId, @NotNull @DecimalMin(value = "0") BigDecimal countedQty) {
    }

    public record RecordCountsRequest(@NotEmpty List<@Valid CountEntry> lines) {
    }
}
