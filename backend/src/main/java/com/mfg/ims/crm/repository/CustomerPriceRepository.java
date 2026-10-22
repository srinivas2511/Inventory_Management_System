package com.mfg.ims.crm.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mfg.ims.crm.domain.CustomerPrice;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long>, JpaSpecificationExecutor<CustomerPrice> {

    /** Active prices that apply on a date for a quantity: this customer's own rows and the standard (null customer) rows. */
    @Query("""
            SELECT p FROM CustomerPrice p
            WHERE p.productId = :productId AND p.active = true
              AND (p.customerId = :customerId OR p.customerId IS NULL)
              AND p.minQty <= :qty
              AND p.validFrom <= :onDate AND (p.validTo IS NULL OR p.validTo >= :onDate)
            """)
    List<CustomerPrice> findApplicable(@Param("customerId") Long customerId, @Param("productId") Long productId,
                                       @Param("qty") BigDecimal qty, @Param("onDate") LocalDate onDate);
}
