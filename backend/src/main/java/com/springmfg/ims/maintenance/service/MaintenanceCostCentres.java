package com.springmfg.ims.maintenance.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.controlling.domain.CostCentre;
import com.springmfg.ims.controlling.repository.CostCentreRepository;
import com.springmfg.ims.masterdata.machine.MachineRepository;

/** Finds the cost centre a machine's maintenance cost is charged to. */
@Component
public class MaintenanceCostCentres {

    static final String DEFAULT_COST_CENTRE_SETTING = "controlling.production_cost_centre_id";

    private final MachineRepository machines;
    private final CostCentreRepository costCentres;
    private final SystemSettingService settings;

    public MaintenanceCostCentres(MachineRepository machines, CostCentreRepository costCentres,
                                  SystemSettingService settings) {
        this.machines = machines;
        this.costCentres = costCentres;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Optional<CostCentre> byId(Long costCentreId) {
        return costCentres.findById(costCentreId);
    }

    /** The machine's own cost centre, else the production cost centre from the controlling settings, else empty. */
    @Transactional(readOnly = true)
    public Optional<CostCentre> forMachine(Long machineId) {
        Long own = machines.findById(machineId).map(m -> m.getCostCentreId()).orElse(null);
        if (own != null) {
            return costCentres.findById(own);
        }
        long fallback = settings.getInt(DEFAULT_COST_CENTRE_SETTING, 0);
        return fallback > 0 ? costCentres.findById(fallback) : Optional.empty();
    }
}
