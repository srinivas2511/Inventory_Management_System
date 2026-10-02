package com.springmfg.ims.maintenance.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.springmfg.ims.maintenance.domain.MaintenanceRequest;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    @Query("SELECT r FROM MaintenanceRequest r WHERE (:status IS NULL OR r.status = :status) " +
           "AND (:requestType IS NULL OR r.requestType = :requestType)")
    Page<MaintenanceRequest> search(@Param("status") String status,
                                    @Param("requestType") String requestType,
                                    Pageable pageable);

    List<MaintenanceRequest> findByStatus(String status);

    @Query("SELECT COUNT(r) FROM MaintenanceRequest r WHERE r.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<String> statuses);
}
