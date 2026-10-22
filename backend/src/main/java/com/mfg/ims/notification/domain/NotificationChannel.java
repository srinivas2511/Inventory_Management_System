package com.mfg.ims.notification.domain;

/** A delivery channel. A template may be BOTH (see {@link NotificationTemplate#channels()}); a notification never is. */
public enum NotificationChannel {
    EMAIL, IN_APP
}
