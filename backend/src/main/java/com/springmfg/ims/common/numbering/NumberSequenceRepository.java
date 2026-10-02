package com.springmfg.ims.common.numbering;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NumberSequenceRepository extends JpaRepository<NumberSequence, NumberSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM NumberSequence s WHERE s.prefix = :prefix AND s.seqYear = :year")
    Optional<NumberSequence> findForUpdate(@Param("prefix") String prefix, @Param("year") int year);
}
