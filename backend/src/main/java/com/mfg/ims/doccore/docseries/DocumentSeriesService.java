package com.mfg.ims.doccore.docseries;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mfg.ims.common.exception.BusinessRuleException;
import com.mfg.ims.common.exception.ErrorCode;
import com.mfg.ims.common.exception.NotFoundException;

@Service
public class DocumentSeriesService {

    private final DocumentSeriesRepository repo;
    private final DocumentSeriesCounterRepository counters;

    private static final Pattern TOKEN = Pattern.compile("\\{([^}]+)}");

    public DocumentSeriesService(DocumentSeriesRepository repo,
                                  DocumentSeriesCounterRepository counters) {
        this.repo = repo;
        this.counters = counters;
    }

    @Transactional(readOnly = true)
    public List<DocumentSeriesDto> list(String documentKind) {
        if (documentKind != null && !documentKind.isBlank()) {
            return repo.findByCompanyIdAndDocumentKindAndActiveTrueOrderBySeriesCode(1L, documentKind)
                    .stream().map(DocumentSeriesDto::from).toList();
        }
        return repo.findByCompanyIdAndActiveTrueOrderByDocumentKindAscSeriesCodeAsc(1L)
                .stream().map(DocumentSeriesDto::from).toList();
    }

    @Transactional
    public DocumentSeriesDto create(SaveDocumentSeriesRequest req) {
        if (repo.existsByCompanyIdAndDocumentKindAndSeriesCode(1L, req.documentKind(), req.seriesCode())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_KEY,
                    "Series code already exists for this document kind: " + req.seriesCode());
        }
        DocumentSeries s = new DocumentSeries();
        applyRequest(s, req);
        return DocumentSeriesDto.from(repo.save(s));
    }

    @Transactional
    public DocumentSeriesDto update(Long id, SaveDocumentSeriesRequest req) {
        DocumentSeries s = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Document series not found: " + id));
        applyRequest(s, req);
        return DocumentSeriesDto.from(repo.save(s));
    }

    @Transactional
    public void deactivate(Long id) {
        DocumentSeries s = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Document series not found: " + id));
        s.setActive(false);
        repo.save(s);
    }

    /**
     * Issues the next formatted document number for the given document kind.
     * Uses a pessimistic lock on the counter row so concurrent requests are serialised.
     */
    @Transactional
    public String nextNumber(Long companyId, String documentKind, LocalDate txDate) {
        DocumentSeries series = repo.findDefault(companyId, documentKind)
                .orElseThrow(() -> new NotFoundException("No default document series for: " + documentKind));

        String periodKey = computePeriodKey(series.getResetPolicy(), txDate);

        DocumentSeriesCounter counter = counters.findForUpdate(series.getId(), periodKey)
                .orElseGet(() -> {
                    DocumentSeriesCounter c = new DocumentSeriesCounter();
                    c.setSeriesId(series.getId());
                    c.setPeriodKey(periodKey);
                    c.setLastValue(0);
                    return counters.save(c);
                });

        long next = counter.getLastValue() + 1;
        counter.setLastValue(next);
        counters.save(counter);

        return applyFormat(series.getFormat(), txDate, next, periodKey);
    }

    private String computePeriodKey(String resetPolicy, LocalDate date) {
        return switch (resetPolicy) {
            case "FY" -> {
                int year = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
                yield year + "-" + String.valueOf(year + 1).substring(2);
            }
            case "MONTH" -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            default -> "FIXED";
        };
    }

    private String applyFormat(String format, LocalDate date, long counter, String periodKey) {
        Matcher m = TOKEN.matcher(format);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String token = m.group(1);
            String replacement = switch (token) {
                case "FY" -> periodKey.contains("-") && periodKey.length() == 7 ? periodKey : computePeriodKey("FY", date);
                case "yyyy" -> String.valueOf(date.getYear());
                case "MM" -> String.format("%02d", date.getMonthValue());
                case "BR" -> "";
                default -> {
                    if (token.matches("0+")) {
                        yield String.format("%0" + token.length() + "d", counter);
                    }
                    yield m.group(0);
                }
            };
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void applyRequest(DocumentSeries s, SaveDocumentSeriesRequest req) {
        s.setDocumentKind(req.documentKind().toUpperCase().trim());
        s.setSeriesCode(req.seriesCode().toUpperCase().trim());
        s.setBranchId(req.branchId());
        s.setFormat(req.format().trim());
        s.setNumbering(req.numbering() != null ? req.numbering() : "AUTO");
        s.setResetPolicy(req.resetPolicy() != null ? req.resetPolicy() : "FY");
        s.setDefault(req.isDefault());
        s.setStatutory(req.statutory());
    }
}
