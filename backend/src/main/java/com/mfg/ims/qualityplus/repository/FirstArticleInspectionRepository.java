package com.mfg.ims.qualityplus.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.common.search.SearchText;
import com.mfg.ims.qualityplus.domain.FaiStatus;
import com.mfg.ims.qualityplus.domain.FirstArticleInspection;

import jakarta.persistence.LockModeType;

public interface FirstArticleInspectionRepository extends JpaRepository<FirstArticleInspection, Long> {

    @Query("SELECT f FROM FirstArticleInspection f WHERE " +
           "(:q = '' OR LOWER(f.faiNumber) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status = '' OR CAST(f.status AS string) = :status)")
    Page<FirstArticleInspection> searchNormalised(@Param("q") String q, @Param("status") String status,
                                                  Pageable pageable);

    default Page<FirstArticleInspection> search(String q, String status, Pageable pageable) {
        return searchNormalised(SearchText.normalise(q), SearchText.normalise(status), pageable);
    }

    boolean existsByProductIdAndStatusIn(Long productId, Collection<FaiStatus> statuses);

    boolean existsByProductIdAndStatus(Long productId, FaiStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FirstArticleInspection f WHERE f.id = :id")
    Optional<FirstArticleInspection> findByIdForUpdate(@Param("id") Long id);
}
