package com.mfg.ims.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mfg.ims.common.settings.SystemSettingService;
import com.mfg.ims.iam.domain.User;
import com.mfg.ims.iam.repository.UserRepository;
import com.mfg.ims.notification.domain.Notification;
import com.mfg.ims.notification.domain.NotificationChannel;
import com.mfg.ims.notification.domain.NotificationStatus;
import com.mfg.ims.notification.repository.NotificationRepository;
import com.mfg.ims.notification.service.EmailSender;
import com.mfg.ims.notification.service.NotificationDeliveryService;
import com.mfg.ims.notification.service.NotificationDeliveryService.Outcome;

class NotificationDeliveryServiceTest {

    private NotificationRepository repo;
    private UserRepository users;
    private EmailSender sender;
    private SystemSettingService settings;
    private NotificationDeliveryService service;

    @BeforeEach
    void setUp() {
        repo = mock(NotificationRepository.class);
        users = mock(UserRepository.class);
        sender = mock(EmailSender.class);
        settings = mock(SystemSettingService.class);
        when(settings.getInt(eq("notification.email.max_attempts"), anyInt())).thenReturn(5);
        when(settings.getInt(eq("notification.email.retry_base_seconds"), anyInt())).thenReturn(60);
        service = new NotificationDeliveryService(repo, users, sender, settings);
    }

    private Notification pending(int attempts) {
        Notification n = new Notification();
        n.setChannel(NotificationChannel.EMAIL);
        n.setStatus(NotificationStatus.PENDING);
        n.setRecipientEmail("buyer@supplier.example");
        n.setSubject("Purchase order PO/26/00007 approved");
        n.setBody("Please confirm.");
        n.setAttempts(attempts);
        when(repo.findByIdForUpdate(1L)).thenReturn(Optional.of(n));
        return n;
    }

    @Test
    void aSuccessfulSendMarksTheMessageSentAndClearsRetryState() {
        Notification n = pending(0);
        n.setLastError("old error");
        n.setNextAttemptAt(Instant.now().minusSeconds(5));

        assertThat(service.deliver(1L)).isEqualTo(Outcome.SENT);

        verify(sender).send("buyer@supplier.example", "Purchase order PO/26/00007 approved", "Please confirm.");
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(n.getSentAt()).isNotNull();
        assertThat(n.getNextAttemptAt()).isNull();
        assertThat(n.getLastError()).isNull();
    }

    @Test
    void aFailureSchedulesARetryAfterTheBaseDelay() {
        Notification n = pending(0);
        doThrow(new IllegalStateException("Mail server connection failed")).when(sender).send(any(), any(), any());

        Instant before = Instant.now();
        assertThat(service.deliver(1L)).isEqualTo(Outcome.RETRY_SCHEDULED);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.getAttempts()).isEqualTo(1);
        assertThat(n.getNextAttemptAt()).isBetween(before.plusSeconds(59), Instant.now().plusSeconds(61));
        assertThat(n.getLastError()).isEqualTo("IllegalStateException: Mail server connection failed");
        assertThat(n.getSentAt()).isNull();
    }

    @Test
    void theRetryDelayDoublesEveryAttemptAndIsCappedAtOneHour() {
        long[] expectedSeconds = { 60, 120, 240, 480 };
        doThrow(new IllegalStateException("down")).when(sender).send(any(), any(), any());
        for (int attemptsBefore = 0; attemptsBefore < expectedSeconds.length; attemptsBefore++) {
            Notification n = pending(attemptsBefore);
            Instant before = Instant.now();
            assertThat(service.deliver(1L)).isEqualTo(Outcome.RETRY_SCHEDULED);
            long delay = Duration.between(before, n.getNextAttemptAt()).toSeconds();
            assertThat(delay).as("after attempt %d", attemptsBefore + 1)
                    .isBetween(expectedSeconds[attemptsBefore] - 1, expectedSeconds[attemptsBefore] + 1);
        }

        when(settings.getInt(eq("notification.email.max_attempts"), anyInt())).thenReturn(100);
        Notification late = pending(14);
        Instant before = Instant.now();
        assertThat(service.deliver(1L)).isEqualTo(Outcome.RETRY_SCHEDULED);
        assertThat(Duration.between(before, late.getNextAttemptAt()).toSeconds()).isBetween(3599L, 3601L);
    }

    @Test
    void afterTheLastAllowedAttemptTheMessageFailsForGood() {
        Notification n = pending(4);
        doThrow(new IllegalStateException("still down")).when(sender).send(any(), any(), any());

        assertThat(service.deliver(1L)).isEqualTo(Outcome.FAILED);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.getAttempts()).isEqualTo(5);
        assertThat(n.getNextAttemptAt()).isNull();
    }

    @Test
    void theAttemptLimitIsASetting() {
        when(settings.getInt(eq("notification.email.max_attempts"), anyInt())).thenReturn(1);
        Notification n = pending(0);
        doThrow(new IllegalStateException("down")).when(sender).send(any(), any(), any());

        assertThat(service.deliver(1L)).isEqualTo(Outcome.FAILED);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void messagesThatAreNotDueOrNotPendingEmailAreLeftAlone() {
        Notification sent = pending(1);
        sent.setStatus(NotificationStatus.SENT);
        assertThat(service.deliver(1L)).isEqualTo(Outcome.SKIPPED);

        Notification inApp = pending(0);
        inApp.setChannel(NotificationChannel.IN_APP);
        assertThat(service.deliver(1L)).isEqualTo(Outcome.SKIPPED);

        Notification notYet = pending(1);
        notYet.setNextAttemptAt(Instant.now().plusSeconds(600));
        assertThat(service.deliver(1L)).isEqualTo(Outcome.SKIPPED);
        assertThat(notYet.getAttempts()).isEqualTo(1);

        when(repo.findByIdForUpdate(2L)).thenReturn(Optional.empty());
        assertThat(service.deliver(2L)).isEqualTo(Outcome.SKIPPED);

        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void aUserRecipientIsAddressedByTheirCurrentEmail() {
        Notification n = pending(0);
        n.setRecipientEmail(null);
        n.setUserId(7L);
        User u = mock(User.class);
        when(u.getEmail()).thenReturn("planner@ims.example");
        when(users.findById(7L)).thenReturn(Optional.of(u));

        assertThat(service.deliver(1L)).isEqualTo(Outcome.SENT);
        verify(sender).send(eq("planner@ims.example"), any(), any());
    }

    @Test
    void aRecipientWithoutAnAddressCountsAsAFailedAttempt() {
        Notification n = pending(0);
        n.setRecipientEmail(null);
        n.setUserId(7L);
        User u = mock(User.class);
        when(u.getEmail()).thenReturn(" ");
        when(users.findById(7L)).thenReturn(Optional.of(u));

        assertThat(service.deliver(1L)).isEqualTo(Outcome.RETRY_SCHEDULED);
        assertThat(n.getLastError()).contains("no e-mail address");
        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void theRecordedErrorIsOneBoundedLineAndNeverAStackTrace() {
        Notification n = pending(0);
        String detail = "Couldn't connect to host smtp.example:587\n\tat org.example.Secret.connect(Secret.java:1)\n"
                + "password=hunter2";
        doThrow(new RuntimeException(detail)).when(sender).send(any(), any(), any());
        service.deliver(1L);
        assertThat(n.getLastError()).isEqualTo("RuntimeException: Couldn't connect to host smtp.example:587")
                .doesNotContain("hunter2").doesNotContain("\n");

        Notification huge = pending(0);
        doThrow(new RuntimeException("x".repeat(1000))).when(sender).send(any(), any(), any());
        service.deliver(1L);
        assertThat(huge.getLastError()).hasSize(300);
    }
}
