package com.springmfg.ims.qualityplus.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.qualityplus.domain.CapaRecord;
import com.springmfg.ims.qualityplus.domain.CapaSourceType;
import com.springmfg.ims.qualityplus.domain.CapaStatus;

import jakarta.persistence.LockModeType;

public interface CapaRecordRepository extends JpaRepository<CapaRecord, Long> {

    @Query("SELECT c FROM CapaRecord c WHERE " +
           "(:q = '' OR LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(c.capaNumber) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status = '' OR CAST(c.status AS string) = :status) " +
           "AND (:source = '' OR CAST(c.sourceType AS string) = :source)")
    Page<CapaRecord> searchNormalised(@Param("q") String q, @Param("status") String status,
                                      @Param("source") String source, Pageable pageable);

    default Page<CapaRecord> search(String q, String status, String source, Pageable pageable) {
        return searchNormalised(SearchText.normalise(q), SearchText.normalise(status),
                SearchText.normalise(source), pageable);
    }

    boolean existsBySourceTypeAndSourceIdAndStatusNot(CapaSourceType sourceType, Long sourceId, CapaStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CapaRecord c WHERE c.id = :id")
    Optional<CapaRecord> findByIdForUpdate(@Param("id") Long id);
}
