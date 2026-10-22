package com.mfg.ims.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mfg.ims.iam.domain.User;
import com.mfg.ims.iam.repository.UserRepository;
import com.mfg.ims.notification.domain.Notification;
import com.mfg.ims.notification.domain.NotificationChannel;
import com.mfg.ims.notification.domain.NotificationStatus;
import com.mfg.ims.notification.domain.NotificationSubscription;
import com.mfg.ims.notification.domain.NotificationTemplate;
import com.mfg.ims.notification.repository.NotificationRepository;
import com.mfg.ims.notification.repository.NotificationSubscriptionRepository;
import com.mfg.ims.notification.repository.NotificationTemplateRepository;
import com.mfg.ims.notification.service.NotificationService;
import com.mfg.ims.notification.service.NotificationService.Request;

class NotificationServiceTest {

    private NotificationTemplateRepository templates;
    private NotificationSubscriptionRepository subscriptions;
    private NotificationRepository notifications;
    private UserRepository users;
    private NotificationService service;
    private final List<Notification> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        templates = mock(NotificationTemplateRepository.class);
        subscriptions = mock(NotificationSubscriptionRepository.class);
        notifications = mock(NotificationRepository.class);
        users = mock(UserRepository.class);
        service = new NotificationService(templates, subscriptions, notifications, users);
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(subscriptions.findByUserIdAndTemplateCode(any(), anyString())).thenReturn(List.of());
    }

    private NotificationTemplate template(String code, String channel) {
        NotificationTemplate t = new NotificationTemplate();
        t.setTemplateCode(code);
        t.setChannel(channel);
        t.setSubjectTemplate("PO {{poNumber}} approved");
        t.setBodyTemplate("Hello {{name}}, PO {{poNumber}} is approved.");
        when(templates.findByTemplateCode(code)).thenReturn(Optional.of(t));
        return t;
    }

    private User user(long id, boolean active, String email) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.isActive()).thenReturn(active);
        when(u.getEmail()).thenReturn(email);
        when(users.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    private static Map<String, Object> payload() {
        return Map.of("poNumber", "PO/26/00007", "name", "Asha");
    }

    @Test
    void aBothTemplateCreatesAnInAppRowAndAnEmailRowForTheOutbox() {
        template("PO_APPROVED", "BOTH");
        user(7, true, "asha@ims.example");

        int created = service.send(Request.toUser("PO_APPROVED", 7L, payload(), "PURCHASE_ORDER", 3L, null));

        assertThat(created).isEqualTo(2);
        Notification inApp = byChannel(NotificationChannel.IN_APP);
        assertThat(inApp.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(inApp.getSentAt()).isNotNull();
        assertThat(inApp.getSubject()).isEqualTo("PO PO/26/00007 approved");
        assertThat(inApp.getBody()).isEqualTo("Hello Asha, PO PO/26/00007 is approved.");
        assertThat(inApp.getEntityType()).isEqualTo("PURCHASE_ORDER");
        assertThat(inApp.getEntityId()).isEqualTo(3L);
        assertThat(inApp.getUserId()).isEqualTo(7L);

        Notification email = byChannel(NotificationChannel.EMAIL);
        assertThat(email.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(email.getNextAttemptAt()).isNotNull();
        assertThat(email.getSentAt()).isNull();
        assertThat(email.getAttempts()).isZero();
    }

    @Test
    void aUserWhoUnsubscribedFromAChannelDoesNotGetIt() {
        template("PO_APPROVED", "BOTH");
        user(7, true, "asha@ims.example");
        NotificationSubscription off = new NotificationSubscription();
        off.setChannel(NotificationChannel.EMAIL);
        off.setEnabled(false);
        NotificationSubscription on = new NotificationSubscription();
        on.setChannel(NotificationChannel.IN_APP);
        on.setEnabled(true);
        when(subscriptions.findByUserIdAndTemplateCode(7L, "PO_APPROVED")).thenReturn(List.of(off, on));

        assertThat(service.send(Request.toUser("PO_APPROVED", 7L, payload(), null, null, null))).isEqualTo(1);
        assertThat(saved).singleElement().extracting(Notification::getChannel).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    void aUserWithNothingEnabledGetsNothing() {
        template("PO_APPROVED", "BOTH");
        user(7, true, "asha@ims.example");
        NotificationSubscription a = new NotificationSubscription();
        a.setChannel(NotificationChannel.EMAIL);
        a.setEnabled(false);
        NotificationSubscription b = new NotificationSubscription();
        b.setChannel(NotificationChannel.IN_APP);
        b.setEnabled(false);
        when(subscriptions.findByUserIdAndTemplateCode(7L, "PO_APPROVED")).thenReturn(List.of(a, b));

        assertThat(service.send(Request.toUser("PO_APPROVED", 7L, payload(), null, null, null))).isZero();
        assertThat(saved).isEmpty();
    }

    @Test
    void theCallerCanNarrowTheChannelsFurtherThanTheTemplate() {
        template("LOW_STOCK", "BOTH");
        user(7, true, "asha@ims.example");

        Request inAppOnly = Request.toUser("LOW_STOCK", 7L, payload(), null, null, null)
                .restrictedTo(NotificationService.inAppOnly());
        assertThat(service.send(inAppOnly)).isEqualTo(1);
        assertThat(saved).singleElement().extracting(Notification::getChannel).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    void aTemplateOnlyEverUsesItsOwnChannels() {
        template("CAPA_ASSIGNED", "IN_APP");
        user(7, true, "asha@ims.example");

        Request both = Request.toUser("CAPA_ASSIGNED", 7L, payload(), null, null, null)
                .restrictedTo(Set.of(NotificationChannel.EMAIL, NotificationChannel.IN_APP));
        assertThat(service.send(both)).isEqualTo(1);
        assertThat(saved).singleElement().extracting(Notification::getChannel).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    void aUserWithoutAnEmailAddressOnlyGetsTheInAppMessage() {
        template("PO_APPROVED", "BOTH");
        user(7, true, "  ");

        assertThat(service.send(Request.toUser("PO_APPROVED", 7L, payload(), null, null, null))).isEqualTo(1);
        assertThat(saved).singleElement().extracting(Notification::getChannel).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    void anExternalAddressOnlyGetsEmailWithoutAUserId() {
        template("PO_APPROVED_SUPPLIER", "EMAIL");

        int created = service.send(Request.toEmail("PO_APPROVED_SUPPLIER", " Buyer@Supplier.Example ", payload(),
                "PURCHASE_ORDER", 3L, null));

        assertThat(created).isEqualTo(1);
        Notification n = saved.get(0);
        assertThat(n.getUserId()).isNull();
        assertThat(n.getRecipientEmail()).isEqualTo("Buyer@Supplier.Example");
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void anInAppTemplateCannotBeSentToAnExternalAddress() {
        template("PO_APPROVED", "IN_APP");
        assertThat(service.send(Request.toEmail("PO_APPROVED", "someone@example.com", payload(), null, null, null)))
                .isZero();
        assertThat(saved).isEmpty();
    }

    @Test
    void aBlankExternalAddressSendsNothing() {
        template("PO_APPROVED_SUPPLIER", "EMAIL");
        assertThat(service.send(Request.toEmail("PO_APPROVED_SUPPLIER", " ", payload(), null, null, null))).isZero();
        assertThat(service.send(Request.toEmail("PO_APPROVED_SUPPLIER", null, payload(), null, null, null))).isZero();
    }

    @Test
    void theSameDedupKeyNeverCreatesASecondRowPerRecipientAndChannel() {
        template("PO_APPROVED", "BOTH");
        user(7, true, "asha@ims.example");
        when(notifications.existsByDedupKey("PO_APPROVED:3:IN_APP:7")).thenReturn(true);

        assertThat(service.send(Request.toUser("PO_APPROVED", 7L, payload(), null, null, "PO_APPROVED:3")))
                .isEqualTo(1);
        assertThat(saved).singleElement().satisfies(n -> {
            assertThat(n.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(n.getDedupKey()).isEqualTo("PO_APPROVED:3:EMAIL:7");
        });
    }

    @Test
    void externalDedupKeysUseTheLowerCasedAddress() {
        template("PO_APPROVED_SUPPLIER", "EMAIL");
        service.send(Request.toEmail("PO_APPROVED_SUPPLIER", "Buyer@Supplier.Example", payload(), null, null, "K"));
        assertThat(saved.get(0).getDedupKey()).isEqualTo("K:EMAIL:buyer@supplier.example");
    }

    @Test
    void unknownInactiveTemplatesAndInactiveOrMissingUsersSendNothing() {
        NotificationTemplate off = template("OFF", "BOTH");
        off.setActive(false);
        user(7, true, "asha@ims.example");
        assertThat(service.send(Request.toUser("OFF", 7L, payload(), null, null, null))).isZero();
        assertThat(service.send(Request.toUser("NOPE", 7L, payload(), null, null, null))).isZero();

        template("PO_APPROVED", "BOTH");
        user(8, false, "gone@ims.example");
        assertThat(service.send(Request.toUser("PO_APPROVED", 8L, payload(), null, null, null))).isZero();
        when(users.findById(9L)).thenReturn(Optional.empty());
        assertThat(service.send(Request.toUser("PO_APPROVED", 9L, payload(), null, null, null))).isZero();
        assertThat(saved).isEmpty();
    }

    @Test
    void sendToPermissionHoldersFansOutToEveryActiveHolder() {
        template("LOW_STOCK", "IN_APP");
        user(1, true, "a@ims.example");
        user(2, true, "b@ims.example");
        user(3, false, "c@ims.example");
        when(users.findActiveIdsByPermission("ALERT_VIEW")).thenReturn(List.of(1L, 2L, 3L));

        int created = service.sendToPermissionHolders("ALERT_VIEW",
                new Request("LOW_STOCK", null, null, payload(), "ALERT", 5L, "ALERT:5", null));

        assertThat(created).isEqualTo(2);
        assertThat(saved).extracting(Notification::getUserId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(saved).extracting(Notification::getDedupKey)
                .containsExactlyInAnyOrder("ALERT:5:IN_APP:1", "ALERT:5:IN_APP:2");
    }

    @Test
    void subjectsAreBoundedTo200Characters() {
        NotificationTemplate t = template("LONG", "IN_APP");
        t.setSubjectTemplate("{{name}}");
        user(7, true, "asha@ims.example");
        service.send(Request.toUser("LONG", 7L, Map.of("name", "n".repeat(500)), null, null, null));
        assertThat(saved.get(0).getSubject()).hasSize(200);
    }

    private Notification byChannel(NotificationChannel channel) {
        return saved.stream().filter(n -> n.getChannel() == channel).findFirst().orElseThrow();
    }
}
