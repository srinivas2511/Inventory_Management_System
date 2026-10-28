package com.mfg.ims.finance.openitem;

import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;
import java.util.List;

public interface OpenItemRepository extends JpaRepository<OpenItem, Long> {

    List<OpenItem> findByPartyTypeAndPartyIdAndBalanceGreaterThanOrderByDueDateAsc(
            String partyType, Long partyId, BigDecimal zero);

    List<OpenItem> findByCompanyIdAndBalanceGreaterThanOrderByDueDateAsc(
            Long companyId, BigDecimal zero);
}
