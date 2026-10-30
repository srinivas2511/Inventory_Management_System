package com.mfg.ims.finance.tds;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class TdsEntryService {

    private final TdsEntryRepository entryRepo;
    private final TdsSectionRepository sectionRepo;

    public TdsEntryService(TdsEntryRepository entryRepo, TdsSectionRepository sectionRepo) {
        this.entryRepo = entryRepo;
        this.sectionRepo = sectionRepo;
    }

    public List<TdsEntryDto> list(Long partyId) {
        List<TdsEntry> entries = partyId != null
                ? entryRepo.findByParty(1L, partyId)
                : entryRepo.findByCompanyIdOrderByDeductionDateDesc(1L);
        return entries.stream().map(this::toDto).toList();
    }

    @Transactional
    public TdsEntryDto create(TdsEntryDto req, Long userId) {
        TdsSection section = null;
        if (req.sectionId() != null) {
            section = sectionRepo.findById(req.sectionId())
                    .orElseThrow(() -> new IllegalArgumentException("TDS section not found: " + req.sectionId()));
        }
        TdsEntry entry = new TdsEntry();
        entry.setDirection(req.direction());
        entry.setPartyType(req.partyType());
        entry.setPartyId(req.partyId());
        entry.setSection(section);
        entry.setBaseAmount(req.baseAmount());
        entry.setRate(req.rate());
        entry.setTdsAmount(req.tdsAmount());
        entry.setDeductionDate(req.deductionDate());
        entry.setChallanNo(req.challanNo());
        entry.setFiscalYear(req.fiscalYear());
        entry.setCreatedBy(userId);
        entry = entryRepo.save(entry);
        return toDto(entry);
    }

    public List<TdsSummaryDto> summary() {
        List<TdsEntry> all = entryRepo.findByCompanyIdOrderByDeductionDateDesc(1L);
        Map<String, List<TdsEntry>> bySection = all.stream()
                .filter(e -> e.getSection() != null)
                .collect(Collectors.groupingBy(e -> e.getSection().getSection()));

        return bySection.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> {
                    String sectionCode = entry.getKey();
                    List<TdsEntry> entries = entry.getValue();
                    String description = entries.isEmpty() ? null : entries.get(0).getSection().getDescription();
                    BigDecimal totalBase = entries.stream()
                            .map(e -> e.getBaseAmount() != null ? e.getBaseAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal totalTds = entries.stream()
                            .map(e -> e.getTdsAmount() != null ? e.getTdsAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new TdsSummaryDto(sectionCode, description, entries.size(), totalBase, totalTds);
                })
                .toList();
    }

    private TdsEntryDto toDto(TdsEntry e) {
        String sectionCode = e.getSection() != null ? e.getSection().getSection() : null;
        String sectionDesc = e.getSection() != null ? e.getSection().getDescription() : null;
        Long sectionId = e.getSection() != null ? e.getSection().getId() : null;
        return new TdsEntryDto(
                e.getId(), e.getDirection(), e.getPartyType(), e.getPartyId(),
                sectionId, sectionCode, sectionDesc,
                e.getBaseAmount(), e.getRate(), e.getTdsAmount(),
                e.getDeductionDate(), e.getChallanNo(), e.getFiscalYear(),
                e.getCreatedAt());
    }
}
