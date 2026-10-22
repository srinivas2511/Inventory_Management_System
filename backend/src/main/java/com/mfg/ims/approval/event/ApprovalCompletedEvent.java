package com.mfg.ims.approval.event;

public record ApprovalCompletedEvent(String documentType, Long documentId) {
}
