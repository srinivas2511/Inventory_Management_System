package com.mfg.ims.finance.tax.repository;

import com.mfg.ims.finance.tax.domain.GstState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstStateRepository extends JpaRepository<GstState, String> {
}
