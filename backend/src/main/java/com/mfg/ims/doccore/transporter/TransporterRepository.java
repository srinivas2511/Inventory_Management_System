package com.mfg.ims.doccore.transporter;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransporterRepository extends JpaRepository<Transporter, Long> {
    List<Transporter> findByCompanyIdAndActiveTrueOrderByName(Long companyId);
}
