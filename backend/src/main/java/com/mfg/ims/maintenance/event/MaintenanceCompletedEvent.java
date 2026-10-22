package com.mfg.ims.maintenance.event;

/** Published when a maintenance request is completed; the labour cost is posted to the GL once that commits. */
public record MaintenanceCompletedEvent(Long maintenanceRequestId) {
}
