package com.springmfg.ims.notification.listener;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.springmfg.ims.common.event.AlertRaisedEvent;
import com.springmfg.ims.common.event.CapaAssignedEvent;
import com.springmfg.ims.common.event.PurchaseOrderApprovedEvent;
import com.springmfg.ims.common.settings.SystemSettingService;
import com.springmfg.ims.enterprise.repository.CompanyRepository;
import com.springmfg.ims.notification.domain.NotificationChannel;
import com.springmfg.ims.notification.repository.NotificationTemplateRepository;
import com.springmfg.ims.notification.service.NotificationService;
import com.springmfg.ims.notification.service.NotificationService.Request;
import com.springmfg.ims.partner.domain.Supplier;
import com.springmfg.ims.partner.repository.SupplierRepository;
import com.springmfg.ims.procurement.domain.PurchaseOrder;
import com.springmfg.ims.procurement.repository.PurchaseOrderRepository;

/**
 * Turns business events into notifications. Every handler runs after the originating transaction committed, hands
 * the work to {@link NotificationService} (which uses its own transaction) and swallows its own failures: telling
 * people about something must never fail, or roll back, the thing that happened.
 */
@Component
public class NotificationEventListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListeners.class);
    private static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final NotificationService notifications;
    private final NotificationTemplateRepository templates;
    private final SystemSettingService settings;
    private final PurchaseOrderRepository purchaseOrders;
    private final SupplierRepository suppliers;
    private final CompanyRepository companies;

    public NotificationEventListeners(NotificationService notifications, NotificationTemplateRepository templates,
                                      SystemSettingService settings, PurchaseOrderRepository purchaseOrders,
                                      SupplierRepository suppliers, CompanyRepository companies) {
        this.notifications = notifications;
        this.templates = templates;
        this.settings = settings;
        this.purchaseOrders = purchaseOrders;
        this.suppliers = suppliers;
        this.companies = companies;
    }

    /** Every alert is shown in-app to ALERT_VIEW holders; only alerts at or above the configured severity are e-mailed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlertRaised(AlertRaisedEvent e) {
        try {
            // A type with its own template uses it (switching that template off silences the type); only a type
            // without any template falls back to the generic alert template.
            String code = templates.findByTemplateCode(e.alertType()).isPresent() ? e.alertType() : "ALERT_GENERIC";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("alertType", e.alertType());
            payload.put("severity", e.severity());
            payload.put("message", e.message());
            payload.put("entityType", e.entityType());
            payload.put("entityId", e.entityId());
            Set<NotificationChannel> channels = EnumSet.of(NotificationChannel.IN_APP);
            if (severityRank(e.severity()) >= severityRank(settings.get("notification.alert.email_min_severity",
                    "HIGH"))) {
                channels.add(NotificationChannel.EMAIL);
            }
            notifications.sendToPermissionHolders("ALERT_VIEW", new Request(code, null, null, payload, "ALERT",
                    e.alertId(), "ALERT:" + e.alertId(), channels));
        } catch (RuntimeException ex) {
            log.warn("Could not notify about alert {}: {}", e.alertId(), ex.toString());
        }
    }

    /** The supplier contact is e-mailed the approved PO; the person who submitted it gets an in-app note. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPurchaseOrderApproved(PurchaseOrderApprovedEvent e) {
        try {
            PurchaseOrder po = purchaseOrders.findById(e.purchaseOrderId()).orElse(null);
            if (po == null) {
                return;
            }
            Supplier supplier = suppliers.findById(po.getSupplierId()).orElse(null);
            String supplierName = supplier == null ? "" : supplier.getName();
            String company = companies.findById(1L).map(c -> c.getName()).orElse("");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("poNumber", po.getPoNumber());
            payload.put("supplierName", supplierName);
            payload.put("contactName", supplier == null || supplier.getContactPerson() == null
                    || supplier.getContactPerson().isBlank() ? "Sir/Madam" : supplier.getContactPerson());
            payload.put("orderDate", po.getPoDate() == null ? "" : po.getPoDate().toString());
            payload.put("companyName", company);

            if (supplier != null && supplier.getEmail() != null && !supplier.getEmail().isBlank()) {
                notifications.send(Request.toEmail("PO_APPROVED_SUPPLIER", supplier.getEmail(), payload,
                        "PURCHASE_ORDER", po.getId(), "PO_APPROVED_SUPPLIER:" + po.getId()));
            }
            Long requester = po.getSubmittedBy() != null ? po.getSubmittedBy() : po.getCreatedBy();
            if (requester != null) {
                notifications.send(Request.toUser("PO_APPROVED", requester, payload, "PURCHASE_ORDER", po.getId(),
                        "PO_APPROVED:" + po.getId()));
            }
        } catch (RuntimeException ex) {
            log.warn("Could not notify about approved purchase order {}: {}", e.purchaseOrderId(), ex.toString());
        }
    }

    /** The new assignee is told in-app; assigning a CAPA to yourself needs no notice. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCapaAssigned(CapaAssignedEvent e) {
        try {
            if (e.assignedTo() == null || e.assignedTo().equals(e.assignedBy())) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("capaNumber", e.capaNumber());
            payload.put("title", e.title());
            // No dedup key: one event per change of assignee, and a hand-back to an earlier assignee must still notify.
            notifications.send(Request.toUser("CAPA_ASSIGNED", e.assignedTo(), payload, "CAPA", e.capaId(), null));
        } catch (RuntimeException ex) {
            log.warn("Could not notify about CAPA {}: {}", e.capaNumber(), ex.toString());
        }
    }

    private static int severityRank(String severity) {
        int i = severity == null ? -1 : SEVERITIES.indexOf(severity.trim().toUpperCase());
        return i < 0 ? SEVERITIES.size() : i;
    }
}
