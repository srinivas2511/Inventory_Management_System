package com.springmfg.ims.enterprise.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PlantDtos {

    private PlantDtos() {}

    public record PlantDto(Long id, String plantCode, String name, Long companyId, String address, boolean active) {}

    public record SavePlantRequest(
            @NotBlank @Size(max = 10) String plantCode,
            @NotBlank @Size(max = 100) String name,
            Long companyId,
            @Size(max = 500) String address,
            Boolean active) {}

    /** The plants a user is limited to; {@code allAccess} users see every plant whatever is assigned. */
    public record UserPlantsDto(Long userId, List<Long> plantIds, boolean allAccess) {}

    public record AssignPlantsRequest(@NotNull List<Long> plantIds) {}

    /** One overridable setting with the value that applies and where it comes from (PLANT, COMPANY or SYSTEM). */
    public record PlantSettingDto(String key, String value, String source) {}

    public record SaveSettingRequest(Long plantId, @NotBlank @Size(max = 60) String key,
                                     @NotBlank @Size(max = 200) String value) {}
}
