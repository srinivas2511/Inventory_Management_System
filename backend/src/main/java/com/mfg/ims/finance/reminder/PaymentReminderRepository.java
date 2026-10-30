package com.mfg.ims.finance.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentReminderRepository extends JpaRepository<PaymentReminder, Long> {

    List<PaymentReminder> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<PaymentReminder> findByCompanyIdAndPartyIdOrderByCreatedAtDesc(Long companyId, Long partyId);
}
