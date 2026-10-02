package com.springmfg.ims.approval.event;

public record ApprovalCompletedEvent(String documentType, Long documentId) {
}
