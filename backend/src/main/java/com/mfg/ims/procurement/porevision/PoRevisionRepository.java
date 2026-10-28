package com.mfg.ims.procurement.porevision;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PoRevisionRepository extends JpaRepository<PoRevision, Long> {
    List<PoRevision> findByPoIdOrderByRevisionNoDesc(Long poId);
    Optional<PoRevision> findFirstByPoIdOrderByRevisionNoDesc(Long poId);
}
