package com.mfg.ims.analytics.reportbuilder;

import com.mfg.ims.common.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ReportBuilderService {

    private static final Set<String> ALLOWED_VIEWS = Set.of(
            "bi_sales", "bi_purchase", "bi_inventory", "bi_production");

    private final SavedReportRepository savedReportRepo;
    private final ReportViewCatalogueRepository catalogueRepo;
    private final JdbcTemplate jdbc;

    public ReportBuilderService(SavedReportRepository savedReportRepo,
                                 ReportViewCatalogueRepository catalogueRepo,
                                 JdbcTemplate jdbc) {
        this.savedReportRepo = savedReportRepo;
        this.catalogueRepo = catalogueRepo;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ReportViewCatalogueDto> catalogue() {
        return catalogueRepo.findAll().stream().map(e -> new ReportViewCatalogueDto(
                e.getViewName(), e.getColumnName(), e.getDataType(),
                e.getLabel(), e.getAggregations(), e.getPermissionCode())).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> run(String viewName, List<String> fields) {
        if (!ALLOWED_VIEWS.contains(viewName.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "View not allowed: " + viewName);
        }
        List<ReportViewCatalogueEntry> allowed = catalogueRepo.findByViewName(viewName);
        Set<String> allowedCols = allowed.stream()
                .map(ReportViewCatalogueEntry::getColumnName).collect(Collectors.toSet());

        List<String> safe = fields.stream().filter(allowedCols::contains).collect(Collectors.toList());
        if (safe.isEmpty()) safe = List.copyOf(allowedCols);

        String cols = safe.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(", "));
        String sql = "SELECT " + cols + " FROM ims." + viewName + " LIMIT 1000";
        return jdbc.queryForList(sql);
    }

    @Transactional(readOnly = true)
    public List<SavedReportDto> listSavedReports() {
        return savedReportRepo.findByCompanyIdOrderByIdDesc(1L).stream().map(this::toDto).toList();
    }

    public SavedReportDto createSavedReport(CreateSavedReportRequest req, Long actorId) {
        if (!ALLOWED_VIEWS.contains(req.sourceView().toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "View not allowed: " + req.sourceView());
        }
        SavedReport r = new SavedReport();
        r.setOwnerId(actorId);
        r.setName(req.name());
        r.setSourceView(req.sourceView());
        r.setDefinition(req.definition() != null ? req.definition() : Map.of());
        if (req.shareScope() != null) r.setShareScope(req.shareScope());
        r.setCreatedBy(actorId);
        return toDto(savedReportRepo.save(r));
    }

    @Transactional(readOnly = true)
    public SavedReportDto getSavedReport(Long id) {
        return toDto(savedReportRepo.findByIdAndCompanyId(id, 1L)
                .orElseThrow(() -> new NotFoundException("SavedReport", id)));
    }

    private SavedReportDto toDto(SavedReport r) {
        return new SavedReportDto(r.getId(), r.getName(), r.getSourceView(),
                r.getDefinition(), r.getShareScope(), r.getOwnerId(), r.getCreatedAt());
    }
}
