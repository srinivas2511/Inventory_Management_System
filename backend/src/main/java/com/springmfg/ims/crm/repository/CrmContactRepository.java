package com.springmfg.ims.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.springmfg.ims.crm.domain.CrmContact;

public interface CrmContactRepository extends JpaRepository<CrmContact, Long>, JpaSpecificationExecutor<CrmContact> {}
