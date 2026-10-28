package com.mfg.ims.doccore.customfield;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldRepository extends JpaRepository<CustomFieldDefinition, Long> {
    List<CustomFieldDefinition> findByCompanyIdAndEntityTypeAndActiveTrueOrderBySortOrder(Long companyId, String entityType);
    boolean existsByCompanyIdAndEntityTypeAndFieldKey(Long companyId, String entityType, String fieldKey);
}
