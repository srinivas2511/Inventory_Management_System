package com.springmfg.ims.crm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.crm.domain.CustomerTag;

public interface CustomerTagRepository extends JpaRepository<CustomerTag, Long> {

    boolean existsByTagGroupAndName(String tagGroup, String name);
}
