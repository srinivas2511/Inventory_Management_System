package com.mfg.ims.hrm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.mfg.ims.production.event.OperationCompletedEvent;
import com.mfg.ims.production.event.OperationStartedEvent;

/**
 * Turns production operation events into attendance (HRM-002). It runs after the operation's transaction has
 * committed and swallows its own failures: time capture must never block shop-floor work.
 */
@Component
public class AttendanceCaptureListener {

    private static final Logger log = LoggerFactory.getLogger(AttendanceCaptureListener.class);

    private final AttendanceService attendanceService;

    public AttendanceCaptureListener(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationStarted(OperationStartedEvent event) {
        capture(event.operatorUserId(), event.at(), false, event.operationId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationCompleted(OperationCompletedEvent event) {
        capture(event.operatorUserId(), event.at(), true, event.operationId());
    }

    private void capture(Long operatorUserId, java.time.Instant at, boolean completion, Long operationId) {
        try {
            attendanceService.autoCapture(operatorUserId, at, completion);
        } catch (RuntimeException e) {
            log.warn("Attendance auto-capture failed for operation {}: {}", operationId, e.toString());
        }
    }
}
