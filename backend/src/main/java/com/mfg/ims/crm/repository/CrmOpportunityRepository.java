package com.mfg.ims.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.mfg.ims.crm.domain.CrmOpportunity;

public interface CrmOpportunityRepository
        extends JpaRepository<CrmOpportunity, Long>, JpaSpecificationExecutor<CrmOpportunity> {}
