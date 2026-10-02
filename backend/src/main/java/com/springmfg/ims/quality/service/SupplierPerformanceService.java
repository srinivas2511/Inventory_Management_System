package com.springmfg.ims.quality.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.springmfg.ims.common.exception.NotFoundException;
import com.springmfg.ims.quality.dto.SupplierPerformanceDto;
import com.springmfg.ims.quality.repository.RejectionRepository;

@Service
public class SupplierPerformanceService {

    private final RejectionRepository rejectionRepo;

    public SupplierPerformanceService(RejectionRepository rejectionRepo) {
        this.rejectionRepo = rejectionRepo;
    }

    @Transactional(readOnly = true)
    public SupplierPerformanceDto supplierPerformance(Long supplierId) {
        List<Object[]> rows = rejectionRepo.supplierAcceptanceByMaterial(supplierId);
        if (rows.isEmpty()) {
            throw new NotFoundException("No procurement data for supplier: " + supplierId);
        }

        List<SupplierPerformanceDto.MaterialAcceptance> byMaterial = rows.stream()
                .map(row -> {
                    Long materialId = ((Number) row[0]).longValue();
                    BigDecimal received = toBd(row[1]);
                    BigDecimal accepted = toBd(row[2]);
                    BigDecimal rejected = toBd(row[3]);
                    BigDecimal rate = received.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : accepted.divide(received, 4, RoundingMode.HALF_UP)
                                    .multiply(BigDecimal.valueOf(100));
                    return new SupplierPerformanceDto.MaterialAcceptance(
                            materialId, null, received, accepted, rejected, rate);
                })
                .toList();

        return new SupplierPerformanceDto(supplierId, null, byMaterial);
    }

    private BigDecimal toBd(Object val) {
        if (val == null) return BigDecimal.ZERO;
        return val instanceof BigDecimal bd ? bd : new BigDecimal(val.toString());
    }
}
