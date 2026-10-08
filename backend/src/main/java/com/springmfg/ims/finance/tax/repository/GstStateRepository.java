package com.springmfg.ims.finance.tax.repository;

import com.springmfg.ims.finance.tax.domain.GstState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GstStateRepository extends JpaRepository<GstState, String> {
}
