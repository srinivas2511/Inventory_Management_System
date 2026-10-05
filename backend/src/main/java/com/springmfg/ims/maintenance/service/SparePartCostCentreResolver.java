package com.springmfg.ims.maintenance.service;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.springmfg.ims.controlling.domain.CostCentre;
import com.springmfg.ims.finance.gl.service.JournalDimensionResolver;
import com.springmfg.ims.maintenance.domain.MaintenanceRequest;
import com.springmfg.ims.maintenance.repository.MaintenanceRequestRepository;
import com.springmfg.ims.maintenance.repository.SparePartIssueLineRepository;

/** Charges the auto-journal of a spare part issue to the cost centre of the machine the part was issued for. */
@Component
public class SparePartCostCentreResolver implements JournalDimensionResolver {

    private final SparePartIssueLineRepository issueRepo;
    private final MaintenanceRequestRepository requestRepo;
    private final MaintenanceCostCentres costCentres;

    public SparePartCostCentreResolver(SparePartIssueLineRepository issueRepo,
                                       MaintenanceRequestRepository requestRepo, MaintenanceCostCentres costCentres) {
        this.issueRepo = issueRepo;
        this.requestRepo = requestRepo;
        this.costCentres = costCentres;
    }

    @Override
    public Optional<Long> costCentreFor(String referenceType, Long referenceId) {
        if (!SparePartService.ISSUE_REFERENCE.equals(referenceType) || referenceId == null) {
            return Optional.empty();
        }
        return issueRepo.findById(referenceId)
                .flatMap(line -> requestRepo.findById(line.getMaintenanceRequestId()))
                .map(MaintenanceRequest::getMachineId)
                .flatMap(costCentres::forMachine)
                .map(CostCentre::getId);
    }
}
