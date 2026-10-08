package com.springmfg.ims.enterprise.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.audit.service.AuditService;
import com.springmfg.ims.common.exception.BusinessRuleException;
import com.springmfg.ims.common.exception.ConflictException;
import com.springmfg.ims.common.exception.ErrorCode;
import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.enterprise.domain.Plant;
import com.springmfg.ims.enterprise.dto.PlantDtos.PlantDto;
import com.springmfg.ims.enterprise.dto.PlantDtos.SavePlantRequest;
import com.springmfg.ims.enterprise.repository.CompanyRepository;
import com.springmfg.ims.enterprise.repository.PlantRepository;

/** Plant master data (Company -> Plant, ENT-001). The company is shared; plants share the master data under it. */
@Service
public class PlantService {

    private final PlantRepository plantRepo;
    private final CompanyRepository companyRepo;
    private final AuditService auditService;

    public PlantService(PlantRepository plantRepo, CompanyRepository companyRepo, AuditService auditService) {
        this.plantRepo = plantRepo;
        this.companyRepo = companyRepo;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<PlantDto> list() {
        return plantRepo.findAll().stream()
                .sorted(java.util.Comparator.comparing(Plant::getId))
                .map(PlantService::toDto).toList();
    }

    @Transactional
    public PlantDto create(SavePlantRequest req) {
        if (plantRepo.findByPlantCode(req.plantCode()).isPresent()) {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Plant code already exists: " + req.plantCode());
        }
        Long companyId = req.companyId() != null ? req.companyId() : defaultCompanyId();
        companyRepo.findById(companyId).orElseThrow(() -> new NotFoundException("Company", companyId));
        Plant plant = new Plant();
        plant.setPlantCode(req.plantCode());
        plant.setName(req.name());
        plant.setCompanyId(companyId);
        plant.setAddress(req.address());
        plant.setActive(req.active() == null || req.active());
        plant = plantRepo.save(plant);
        auditService.record("PLANT_CREATE", "PLANT", String.valueOf(plant.getId()), null, plant.getPlantCode(), null);
        return toDto(plant);
    }

    @Transactional
    public PlantDto update(Long id, SavePlantRequest req) {
        Plant plant = plantRepo.findById(id).orElseThrow(() -> new NotFoundException("Plant", id));
        plantRepo.findByPlantCode(req.plantCode()).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException(ErrorCode.DUPLICATE_KEY, "Plant code already exists: " + req.plantCode());
        });
        boolean wasActive = Boolean.TRUE.equals(plant.getActive());
        boolean nowActive = req.active() == null || req.active();
        if (wasActive && !nowActive && plantRepo.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive())).count() <= 1) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "The last active plant cannot be deactivated");
        }
        String old = plant.getPlantCode() + "|" + plant.getName() + "|" + wasActive;
        plant.setPlantCode(req.plantCode());
        plant.setName(req.name());
        plant.setAddress(req.address());
        plant.setActive(nowActive);
        plant = plantRepo.save(plant);
        auditService.record("PLANT_UPDATE", "PLANT", String.valueOf(id), old,
                plant.getPlantCode() + "|" + plant.getName() + "|" + nowActive, null);
        return toDto(plant);
    }

    private Long defaultCompanyId() {
        return companyRepo.findAll().stream().map(c -> c.getId()).min(Long::compare)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "No company exists"));
    }

    static PlantDto toDto(Plant p) {
        return new PlantDto(p.getId(), p.getPlantCode(), p.getName(), p.getCompanyId(), p.getAddress(),
                Boolean.TRUE.equals(p.getActive()));
    }
}
