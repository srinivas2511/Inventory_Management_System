package com.mfg.ims.common.event;

/**
 * A new alert row was created (a repeat sighting of an open alert does not raise it again). Published by the Alert
 * entity itself, so every code path that raises an alert is covered; listeners run after the creating transaction commits.
 */
public record AlertRaisedEvent(Long alertId, String alertType, String severity, String entityType, Long entityId,
                               String message) {
}
