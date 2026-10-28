package com.mfg.ims.production.barcode;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BarcodeLabelRepository extends JpaRepository<BarcodeLabel, Long> {
    List<BarcodeLabel> findByCompanyIdOrderByIdDesc(Long companyId);
    Optional<BarcodeLabel> findByIdAndCompanyId(Long id, Long companyId);
    List<BarcodeLabel> findByRefTypeAndRefId(String refType, Long refId);
}
