package com.mfg.ims.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mfg.ims.crm.domain.CustomerTag;

public interface CustomerTagRepository extends JpaRepository<CustomerTag, Long> {

    boolean existsByTagGroupAndName(String tagGroup, String name);
}
