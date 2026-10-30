package com.mfg.ims.integration.tally;

import com.mfg.ims.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional
public class TallyVoucherQueueService {

    private final TallyVoucherQueueRepository repo;

    public TallyVoucherQueueService(TallyVoucherQueueRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<TallyVoucherQueueDto> list(String status) {
        List<TallyVoucherQueue> items = (status != null && !status.isBlank())
                ? repo.findByCompanyIdAndStatusOrderByCreatedAtDesc(1L, status)
                : repo.findByCompanyIdOrderByCreatedAtDesc(1L);
        return items.stream().map(TallyVoucherQueueDto::from).toList();
    }

    public TallyVoucherQueueDto approve(Long id, Long actorId) {
        TallyVoucherQueue q = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("TallyVoucherQueue", id));
        q.setStatus("APPROVED");
        q.setApprovedBy(actorId);
        q.setApprovedAt(OffsetDateTime.now());
        q.setUpdatedAt(OffsetDateTime.now());
        q.setUpdatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }

    public TallyVoucherQueueDto reject(Long id, Long actorId) {
        TallyVoucherQueue q = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("TallyVoucherQueue", id));
        q.setStatus("REJECTED");
        q.setUpdatedAt(OffsetDateTime.now());
        q.setUpdatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }

    public TallyVoucherQueueDto markSent(Long id, Long actorId) {
        TallyVoucherQueue q = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("TallyVoucherQueue", id));
        q.setStatus("SENT");
        q.setSentAt(OffsetDateTime.now());
        q.setUpdatedAt(OffsetDateTime.now());
        q.setUpdatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }

    public int countByStatus(String status) {
        return repo.findByCompanyIdAndStatusOrderByCreatedAtDesc(1L, status).size();
    }

    public TallyVoucherQueueDto enqueue(String sourceType, Long sourceId, String voucherType,
                                         String payloadXml, Long actorId) {
        TallyVoucherQueue q = new TallyVoucherQueue();
        q.setSourceType(sourceType);
        q.setSourceId(sourceId);
        q.setVoucherType(voucherType);
        q.setPayloadXml(payloadXml);
        q.setCreatedBy(actorId);
        return TallyVoucherQueueDto.from(repo.save(q));
    }
}
