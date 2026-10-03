package com.springmfg.ims.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.crm.domain.CrmActivity;

public interface CrmActivityRepository extends JpaRepository<CrmActivity, Long>, JpaSpecificationExecutor<CrmActivity> {

    /** Activities logged against a lead before it became a customer join the customer's log on conversion. */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("UPDATE CrmActivity a SET a.customerId = :customerId WHERE a.leadId = :leadId AND a.customerId IS NULL")
    int attachLeadActivitiesToCustomer(@Param("leadId") Long leadId, @Param("customerId") Long customerId);
}
