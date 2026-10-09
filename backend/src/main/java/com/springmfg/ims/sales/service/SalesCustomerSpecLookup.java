package com.springmfg.ims.sales.service;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.quality.service.CustomerSpecLookup;
import com.springmfg.ims.sales.domain.CustomerProductSpec;
import com.springmfg.ims.sales.domain.SalesOrder;
import com.springmfg.ims.sales.repository.CustomerProductSpecRepository;
import com.springmfg.ims.sales.repository.CustomerSpecOverrideRepository;
import com.springmfg.ims.sales.repository.SalesOrderRepository;

/** Sales-side answer to the quality port: customer of the order, then that customer's product spec, then its override. */
@Component
public class SalesCustomerSpecLookup implements CustomerSpecLookup {

    private final SalesOrderRepository orderRepo;
    private final CustomerProductSpecRepository specRepo;
    private final CustomerSpecOverrideRepository overrideRepo;

    public SalesCustomerSpecLookup(SalesOrderRepository orderRepo, CustomerProductSpecRepository specRepo,
                                   CustomerSpecOverrideRepository overrideRepo) {
        this.orderRepo = orderRepo;
        this.specRepo = specRepo;
        this.overrideRepo = overrideRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Limits> find(Long salesOrderId, Long productId, String parameterName) {
        if (salesOrderId == null || productId == null || parameterName == null) {
            return Optional.empty();
        }
        return orderRepo.findById(salesOrderId)
                .map(SalesOrder::getCustomerId)
                .flatMap(customerId -> specRepo.findByCustomerIdAndProductId(customerId, productId))
                .filter(CustomerProductSpec::isActive)
                .flatMap(spec -> overrideRepo
                        .findByCustomerProductSpecIdAndParameterNameAndActiveTrue(spec.getId(), parameterName)
                        .map(o -> new Limits(o.getLowerLimit(), o.getUpperLimit(), spec.getSpecReference())));
    }
}
