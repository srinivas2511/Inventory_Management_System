package com.springmfg.ims.auth;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.springmfg.ims.config.ImsMailProperties;
import com.springmfg.ims.config.ImsSecurityProperties;

/**
 * Sends the reset e-mail after the request transaction commits, off the request thread, so response time does
 * not reveal whether the address belongs to an account.
 */
@Component
class PasswordResetMailListener {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailListener.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ImsMailProperties mail;
    private final Duration tokenTtl;

    PasswordResetMailListener(ObjectProvider<JavaMailSender> mailSender, ImsMailProperties mail,
            ImsSecurityProperties security) {
        this.mailSender = mailSender;
        this.mail = mail;
        this.tokenTtl = security.resetTokenTtl();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPasswordResetRequested(PasswordResetRequested event) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("A password reset was requested but no mail server is configured (spring.mail.host); "
                    + "no e-mail was sent");
            return;
        }
        String base = mail.resetUrlBase().replaceAll("/+$", "");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mail.from());
        message.setTo(event.email());
        message.setSubject("Reset your IMS password");
        message.setText("Hello " + event.fullName() + ",\n\n"
                + "Use this link to choose a new password. It works once and expires in "
                + tokenTtl.toMinutes() + " minutes:\n\n"
                + base + "/reset-password/" + event.rawToken() + "\n\n"
                + "If you did not ask for this, ignore this e-mail; your password has not changed.\n");
        try {
            sender.send(message);
        } catch (RuntimeException e) {
            log.error("Could not send the password reset e-mail: {}", e.getMessage());
        }
    }
}
