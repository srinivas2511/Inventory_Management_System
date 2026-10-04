package com.springmfg.ims.plm.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.plm.domain.Eco;

import jakarta.persistence.LockModeType;

public interface EcoRepository extends JpaRepository<Eco, Long> {

    @Query("SELECT e FROM Eco e WHERE " +
           "(:q = '' OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(e.ecoNumber) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status = '' OR CAST(e.status AS string) = :status)")
    Page<Eco> searchNormalised(@Param("q") String q, @Param("status") String status, Pageable pageable);

    default Page<Eco> search(String q, String status, Pageable pageable) {
        return searchNormalised(SearchText.normalise(q), SearchText.normalise(status), pageable);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Eco e WHERE e.id = :id")
    Optional<Eco> findByIdForUpdate(@Param("id") Long id);
}
