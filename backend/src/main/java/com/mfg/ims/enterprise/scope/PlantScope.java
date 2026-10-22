package com.mfg.ims.enterprise.scope;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.enterprise.domain.Plant;
import com.mfg.ims.enterprise.repository.PlantRepository;

/**
 * The one place that decides which plants the current user may see and work in (ENT-002 .. ENT-004).
 *
 * <p>A user is scoped when assigned to plants and not holding PLANT_ALL_ACCESS; everyone else (including requests
 * with no authenticated user, such as scheduled jobs and after-commit listeners) is unrestricted, so a default
 * single-plant install behaves exactly as it did before plants existed.
 */
@Component
public class PlantScope {

    /** Query helper: {@code (:scoped = FALSE OR x.plantId IN :plantIds)}; plantIds is never empty (a sentinel is used). */
    public record Filter(boolean scoped, List<Long> plantIds) {}

    private static final List<Long> NO_PLANT = List.of(-1L);

    private final PlantRepository plantRepo;
    private final com.mfg.ims.warehouse.repository.WarehouseRepository warehouseRepo;

    public PlantScope(PlantRepository plantRepo,
                      com.mfg.ims.warehouse.repository.WarehouseRepository warehouseRepo) {
        this.plantRepo = plantRepo;
        this.warehouseRepo = warehouseRepo;
    }

    /** The plant a warehouse belongs to, or null when the warehouse is unknown. */
    public Long warehousePlantId(Long warehouseId) {
        if (warehouseId == null) {
            return null;
        }
        return warehouseRepo.findById(warehouseId)
                .map(com.mfg.ims.warehouse.domain.Warehouse::getPlantId).orElse(null);
    }

    /** Stock may only be posted to or read from a warehouse of one of the user's plants. */
    public void assertWarehouseVisible(Long warehouseId) {
        if (!isScoped()) {
            return;
        }
        if (!canSee(warehousePlantId(warehouseId))) {
            throw new BusinessRuleException(ErrorCode.OUT_OF_SCOPE, "Warehouse is not in one of your plants");
        }
    }

    public boolean isScoped() {
        CachedUser u = currentUser();
        return u != null && !u.plantIds().isEmpty();
    }

    /** Plants the current user may work in, or an empty set when unrestricted. */
    public Set<Long> restrictedPlantIds() {
        CachedUser u = currentUser();
        return u == null ? Set.of() : u.plantIds();
    }

    public Filter filter() {
        Set<Long> ids = restrictedPlantIds();
        return ids.isEmpty() ? new Filter(false, NO_PLANT) : new Filter(true, new ArrayList<>(ids));
    }

    /** Warehouses of the user's plants, in the same shape as {@link #filter()} (a sentinel keeps the list non-empty). */
    public Filter warehouseFilter() {
        Set<Long> ids = restrictedPlantIds();
        if (ids.isEmpty()) {
            return new Filter(false, NO_PLANT);
        }
        List<Long> warehouses = warehouseRepo.findIdsByPlantIdIn(ids);
        return new Filter(true, warehouses.isEmpty() ? NO_PLANT : warehouses);
    }

    public boolean canSee(Long plantId) {
        Set<Long> ids = restrictedPlantIds();
        return ids.isEmpty() || (plantId != null && ids.contains(plantId));
    }

    /** A record outside the user's plants is reported as not found, so its existence is not revealed. */
    public void assertVisible(Long plantId, String entity, Object id) {
        if (!canSee(plantId)) {
            throw new NotFoundException(entity, id);
        }
    }

    /**
     * The plant a new document belongs to. A scoped user gets their only plant, or must choose one of theirs; an
     * unrestricted user gets the plant they name or the default (first active) plant.
     */
    public Long resolveForCreate(Long requestedPlantId) {
        Set<Long> ids = restrictedPlantIds();
        if (requestedPlantId != null) {
            Plant plant = plantRepo.findById(requestedPlantId)
                    .orElseThrow(() -> new NotFoundException("Plant", requestedPlantId));
            if (!Boolean.TRUE.equals(plant.getActive())) {
                throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Plant " + plant.getPlantCode() + " is not active");
            }
            if (!ids.isEmpty() && !ids.contains(requestedPlantId)) {
                throw new BusinessRuleException(ErrorCode.OUT_OF_SCOPE, "You are not assigned to plant " + plant.getPlantCode());
            }
            return requestedPlantId;
        }
        if (ids.size() == 1) {
            return ids.iterator().next();
        }
        if (ids.size() > 1) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Choose the plant for this document");
        }
        return defaultPlantId();
    }

    /** The first active plant: where documents without an explicit plant belong. */
    public Long defaultPlantId() {
        return plantRepo.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .map(Plant::getId)
                .min(Long::compare)
                .orElse(null);
    }

    private CachedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CachedUser u) {
            return u;
        }
        return null;
    }
}
