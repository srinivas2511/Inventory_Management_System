package com.mfg.ims.finance.config.repository;

import com.mfg.ims.finance.config.domain.TaxCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaxCodeRepository extends JpaRepository<TaxCode, Long> {
    Optional<TaxCode> findByTaxCode(String taxCode);
    List<TaxCode> findByActiveTrue();
}
