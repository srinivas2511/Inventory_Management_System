package com.mfg.ims.doccore.importjob;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {
    List<ImportJob> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
}
