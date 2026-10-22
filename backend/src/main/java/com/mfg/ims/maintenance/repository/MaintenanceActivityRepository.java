package com.mfg.ims.maintenance.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.maintenance.domain.MaintenanceActivity;

public interface MaintenanceActivityRepository extends JpaRepository<MaintenanceActivity, Long> {

    List<MaintenanceActivity> findByMaintenanceRequestIdOrderByPerformedAtAscIdAsc(Long maintenanceRequestId);

    @Query("SELECT COALESCE(SUM(a.durationMinutes), 0) FROM MaintenanceActivity a "
            + "WHERE a.maintenanceRequestId = :requestId")
    long totalMinutes(@Param("requestId") Long requestId);
}
