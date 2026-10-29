package com.mfg.ims.integration.tally;

import com.mfg.ims.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
public class TallyLedgerMappingService {

    private final TallyLedgerMappingRepository repo;

    public TallyLedgerMappingService(TallyLedgerMappingRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<TallyLedgerMappingDto> list() {
        return repo.findByCompanyIdOrderByGlAccountCode(1L).stream()
                .map(TallyLedgerMappingDto::from).toList();
    }

    public TallyLedgerMappingDto create(CreateTallyLedgerMappingRequest req, Long actorId) {
        TallyLedgerMapping m = new TallyLedgerMapping();
        m.setGlAccountCode(req.glAccountCode());
        m.setTallyLedger(req.tallyLedger());
        m.setNotes(req.notes());
        m.setCreatedBy(actorId);
        return TallyLedgerMappingDto.from(repo.save(m));
    }

    public TallyLedgerMappingDto update(Long id, CreateTallyLedgerMappingRequest req, Long actorId) {
        TallyLedgerMapping m = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("TallyLedgerMapping", id));
        m.setGlAccountCode(req.glAccountCode());
        m.setTallyLedger(req.tallyLedger());
        m.setNotes(req.notes());
        m.setUpdatedBy(actorId);
        m.setUpdatedAt(OffsetDateTime.now());
        return TallyLedgerMappingDto.from(repo.save(m));
    }

    public void delete(Long id) {
        repo.findById(id).orElseThrow(() -> new NotFoundException("TallyLedgerMapping", id));
        repo.deleteById(id);
    }
}
