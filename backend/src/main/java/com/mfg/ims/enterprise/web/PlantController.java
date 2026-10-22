package com.mfg.ims.enterprise.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mfg.ims.enterprise.dto.PlantDtos.AssignPlantsRequest;
import com.mfg.ims.enterprise.dto.PlantDtos.PlantDto;
import com.mfg.ims.enterprise.dto.PlantDtos.PlantSettingDto;
import com.mfg.ims.enterprise.dto.PlantDtos.SavePlantRequest;
import com.mfg.ims.enterprise.dto.PlantDtos.SaveSettingRequest;
import com.mfg.ims.enterprise.dto.PlantDtos.UserPlantsDto;
import com.mfg.ims.enterprise.service.PlantService;
import com.mfg.ims.enterprise.service.PlantSettingService;
import com.mfg.ims.enterprise.service.UserPlantService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/plants")
@Tag(name = "Plants")
public class PlantController {

    private final PlantService plants;
    private final PlantSettingService settings;
    private final UserPlantService userPlants;

    public PlantController(PlantService plants, PlantSettingService settings, UserPlantService userPlants) {
        this.plants = plants;
        this.settings = settings;
        this.userPlants = userPlants;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public List<PlantDto> list() {
        return plants.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public PlantDto create(@Valid @RequestBody SavePlantRequest req) {
        return plants.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public PlantDto update(@PathVariable Long id, @Valid @RequestBody SavePlantRequest req) {
        return plants.update(id, req);
    }

    /** The settings in force for a plant, each with the level (PLANT, COMPANY, SYSTEM) it comes from. */
    @GetMapping("/{id}/settings")
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public List<PlantSettingDto> effective(@PathVariable Long id) {
        return settings.effective(id);
    }

    @GetMapping("/company-settings")
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public List<PlantSettingDto> companyDefaults(@RequestParam(defaultValue = "1") Long companyId) {
        return settings.companyDefaults(companyId);
    }

    /** Sets a plant override, or the company default when plantId is omitted. */
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public void setSetting(@Valid @RequestBody SaveSettingRequest req) {
        settings.set(req);
    }

    @DeleteMapping("/settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public void clearSetting(@RequestParam(required = false) Long plantId, @RequestParam String key) {
        settings.clear(plantId, key);
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public UserPlantsDto userPlants(@PathVariable Long userId) {
        return userPlants.get(userId);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public UserPlantsDto assign(@PathVariable Long userId, @Valid @RequestBody AssignPlantsRequest req) {
        return userPlants.replace(userId, req.plantIds());
    }
}
