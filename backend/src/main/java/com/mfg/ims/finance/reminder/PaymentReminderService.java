package com.mfg.ims.finance.reminder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PaymentReminderService {

    private final PaymentReminderRepository repo;

    public PaymentReminderService(PaymentReminderRepository repo) {
        this.repo = repo;
    }

    public List<PaymentReminderDto> list(Long partyId) {
        List<PaymentReminder> rows = partyId != null
                ? repo.findByCompanyIdAndPartyIdOrderByCreatedAtDesc(1L, partyId)
                : repo.findByCompanyIdOrderByCreatedAtDesc(1L);
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public PaymentReminderDto create(PaymentReminderDto req, Long userId) {
        PaymentReminder reminder = new PaymentReminder();
        reminder.setOpenItemId(req.openItemId());
        reminder.setPartyId(req.partyId());
        reminder.setLevel(req.level());
        reminder.setMode(req.mode() != null ? req.mode() : "MANUAL");
        reminder.setChannel(req.channel());
        reminder.setSentBy(userId);
        return toDto(repo.save(reminder));
    }

    private PaymentReminderDto toDto(PaymentReminder r) {
        return new PaymentReminderDto(r.getId(), r.getOpenItemId(), r.getPartyId(),
                r.getLevel(), r.getMode(), r.getSentAt(), r.getChannel(),
                r.getSentBy(), r.getCreatedAt());
    }
}
