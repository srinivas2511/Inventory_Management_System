package com.mfg.ims.finance.gl.service;

import com.mfg.ims.finance.gl.dto.GlAccountDto;
import com.mfg.ims.finance.gl.repository.GlAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class GlAccountService {

    private final GlAccountRepository glAccountRepo;

    public GlAccountService(GlAccountRepository glAccountRepo) {
        this.glAccountRepo = glAccountRepo;
    }

    public List<GlAccountDto> findActive() {
        return glAccountRepo.findByActiveTrue().stream()
                .map(a -> new GlAccountDto(a.getId(), a.getAccountCode(), a.getName(),
                        a.getAccountType(), a.getNormalBalance(),
                        Boolean.TRUE.equals(a.getAllowDirectPosting()),
                        Boolean.TRUE.equals(a.getActive())))
                .toList();
    }
}
