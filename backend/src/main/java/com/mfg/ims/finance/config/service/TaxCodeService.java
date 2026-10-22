package com.mfg.ims.finance.config.service;

import com.mfg.ims.common.exception.NotFoundException;
import com.mfg.ims.finance.config.domain.TaxCode;
import com.mfg.ims.finance.config.dto.TaxCodeDto;
import com.mfg.ims.finance.config.repository.TaxCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaxCodeService {

    private final TaxCodeRepository taxCodeRepo;

    public TaxCodeService(TaxCodeRepository taxCodeRepo) {
        this.taxCodeRepo = taxCodeRepo;
    }

    public List<TaxCodeDto> findActive() {
        return taxCodeRepo.findByActiveTrue().stream().map(this::toDto).toList();
    }

    public List<TaxCodeDto> findAll() {
        return taxCodeRepo.findAll().stream().map(this::toDto).toList();
    }

    public TaxCodeDto findById(Long id) {
        return toDto(taxCodeRepo.findById(id).orElseThrow(() -> new NotFoundException("TaxCode", id)));
    }

    private TaxCodeDto toDto(TaxCode tc) {
        return new TaxCodeDto(tc.getId(), tc.getTaxCode(), tc.getDescription(),
                tc.getTaxType(), tc.getRatePct(), tc.getGlAccountId(), tc.isActive());
    }
}
