package com.mfg.ims.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.mfg.ims.crm.domain.CrmContact;

public interface CrmContactRepository extends JpaRepository<CrmContact, Long>, JpaSpecificationExecutor<CrmContact> {}
