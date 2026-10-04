package com.springmfg.ims.qualityplus.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.common.search.SearchText;
import com.springmfg.ims.qualityplus.domain.CustomerComplaint;

import jakarta.persistence.LockModeType;

public interface CustomerComplaintRepository extends JpaRepository<CustomerComplaint, Long> {

    @Query("SELECT c FROM CustomerComplaint c WHERE " +
           "(:q = '' OR LOWER(c.complaintNumber) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(COALESCE(c.customerRef, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "   OR LOWER(c.description) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "AND (:status = '' OR CAST(c.status AS string) = :status)")
    Page<CustomerComplaint> searchNormalised(@Param("q") String q, @Param("status") String status,
                                             Pageable pageable);

    default Page<CustomerComplaint> search(String q, String status, Pageable pageable) {
        return searchNormalised(SearchText.normalise(q), SearchText.normalise(status), pageable);
    }

    Optional<CustomerComplaint> findByCapaId(Long capaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CustomerComplaint c WHERE c.id = :id")
    Optional<CustomerComplaint> findByIdForUpdate(@Param("id") Long id);
}
