package com.springmfg.ims.common.event;

/** Published when measured results were saved on an inspection; consumers must wait for the commit. */
public record InspectionResultsSubmittedEvent(Long inspectionId) {
}
