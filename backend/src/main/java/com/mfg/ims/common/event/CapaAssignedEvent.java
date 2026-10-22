package com.mfg.ims.common.event;

/** A CAPA gained a new assignee (on creation or when the assignee changed). */
public record CapaAssignedEvent(Long capaId, String capaNumber, String title, Long assignedTo, Long assignedBy) {
}
