package com.springmfg.ims.production.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.production.domain.MaterialIssue;

public interface MaterialIssueRepository extends JpaRepository<MaterialIssue, Long> {

    List<MaterialIssue> findByProductionOrderId(Long productionOrderId);
}
