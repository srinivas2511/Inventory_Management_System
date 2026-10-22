package com.mfg.ims.mrp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.mfg.ims.mrp.domain.MrpProposal;

public interface MrpProposalRepository extends JpaRepository<MrpProposal, Long>, JpaSpecificationExecutor<MrpProposal> {

    List<MrpProposal> findByMrpRunIdAndProposalTypeAndStatusOrderById(Long mrpRunId, String proposalType, String status);
}
