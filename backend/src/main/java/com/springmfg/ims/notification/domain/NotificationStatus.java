package com.springmfg.ims.notification.domain;

/** PENDING (e-mail waiting in the outbox) -> SENT or FAILED; an in-app notification is SENT, then READ. */
public enum NotificationStatus {
    PENDING, SENT, FAILED, READ
}
