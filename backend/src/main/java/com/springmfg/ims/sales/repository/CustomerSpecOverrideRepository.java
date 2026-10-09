package com.springmfg.ims.sales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.springmfg.ims.sales.domain.CustomerSpecOverride;

public interface CustomerSpecOverrideRepository extends JpaRepository<CustomerSpecOverride, Long> {

    List<CustomerSpecOverride> findByCustomerProductSpecIdAndActiveTrueOrderByParameterName(Long specId);

    Optional<CustomerSpecOverride> findByCustomerProductSpecIdAndParameterName(Long specId, String parameterName);

    Optional<CustomerSpecOverride> findByCustomerProductSpecIdAndParameterNameAndActiveTrue(Long specId,
                                                                                             String parameterName);
}
