package com.mfg.ims.doccore.docseries;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface DocumentSeriesCounterRepository extends JpaRepository<DocumentSeriesCounter, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DocumentSeriesCounter c WHERE c.seriesId = :seriesId AND c.periodKey = :periodKey")
    Optional<DocumentSeriesCounter> findForUpdate(Long seriesId, String periodKey);
}
