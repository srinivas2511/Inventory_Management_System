package com.springmfg.ims.maintenance.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.springmfg.ims.maintenance.domain.SparePart;
import com.springmfg.ims.maintenance.domain.SparePartIssueLine;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response shapes of the spare part master and of issuing parts to a maintenance request. */
public final class SparePartDtos {

    private SparePartDtos() {
    }

    public record SaveSparePartRequest(
            @NotBlank @Size(max = 20) String sparePartCode,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 300) String description,
            @NotBlank @Size(max = 10) String uom,
            @Size(max = 200) String compatibleMachineTypes,
            @NotNull @DecimalMin("0") BigDecimal minStock,
            @NotNull @DecimalMin("0") BigDecimal reorderLevel,
            @NotNull @DecimalMin("0") BigDecimal standardCost,
            Long version) {
    }

    public record SparePartDto(
            Long id,
            String sparePartCode,
            String name,
            String description,
            String uom,
            Long materialId,
            String compatibleMachineTypes,
            BigDecimal minStock,
            BigDecimal reorderLevel,
            BigDecimal standardCost,
            boolean active,
            BigDecimal onHandQty,
            boolean belowMinStock,
            Long version) {

        public static SparePartDto from(SparePart s, BigDecimal onHandQty) {
            BigDecimal onHand = onHandQty == null ? BigDecimal.ZERO : onHandQty;
            return new SparePartDto(s.getId(), s.getSparePartCode(), s.getName(), s.getDescription(), s.getUom(),
                    s.getMaterialId(), s.getCompatibleMachineTypes(), s.getMinStock(), s.getReorderLevel(),
                    s.getStandardCost(), s.isActive(), onHand, onHand.compareTo(s.getMinStock()) < 0,
                    s.getVersion());
        }
    }

    public record IssueSparePartRequest(
            @NotNull Long sparePartId,
            @NotNull Long warehouseId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @Size(max = 300) String remarks) {
    }

    public record SparePartIssueLineDto(
            Long id,
            Long maintenanceRequestId,
            Long sparePartId,
            String sparePartCode,
            String sparePartName,
            Long warehouseId,
            BigDecimal issuedQty,
            BigDecimal unitCost,
            BigDecimal totalCost,
            Long issuedBy,
            Instant issuedAt,
            String remarks) {

        public static SparePartIssueLineDto from(SparePartIssueLine l, SparePart part) {
            return new SparePartIssueLineDto(l.getId(), l.getMaintenanceRequestId(), l.getSparePartId(),
                    part.getSparePartCode(), part.getName(), l.getWarehouseId(), l.getIssuedQty(), l.getUnitCost(),
                    l.totalCost(), l.getIssuedBy(), l.getIssuedAt(), l.getRemarks());
        }
    }
}
