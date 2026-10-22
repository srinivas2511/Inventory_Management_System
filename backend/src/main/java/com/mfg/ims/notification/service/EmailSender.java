package com.mfg.ims.notification.service;

/** The one place that talks to an SMTP server; throws a RuntimeException when the message was not accepted. */
public interface EmailSender {

    void send(String to, String subject, String body);
}
