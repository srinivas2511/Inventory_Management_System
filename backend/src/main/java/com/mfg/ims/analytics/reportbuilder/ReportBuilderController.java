package com.mfg.ims.analytics.reportbuilder;

import com.mfg.ims.auth.service.UserPermissionCache.CachedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report-builder")
public class ReportBuilderController {

    private final ReportBuilderService service;

    public ReportBuilderController(ReportBuilderService service) {
        this.service = service;
    }

    @GetMapping("/catalogue")
    @PreAuthorize("hasAuthority('REPORT_BUILD')")
    public List<ReportViewCatalogueDto> catalogue() {
        return service.catalogue();
    }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('REPORT_BUILD')")
    public List<Map<String, Object>> run(@RequestBody RunReportRequest req) {
        return service.run(req.viewName(), req.fields());
    }

    @GetMapping("/saved")
    @PreAuthorize("hasAuthority('REPORT_BUILD')")
    public List<SavedReportDto> listSaved() {
        return service.listSavedReports();
    }

    @GetMapping("/saved/{id}")
    @PreAuthorize("hasAuthority('REPORT_BUILD')")
    public SavedReportDto getSaved(@PathVariable Long id) {
        return service.getSavedReport(id);
    }

    @PostMapping("/saved")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('REPORT_BUILD')")
    public SavedReportDto createSaved(@RequestBody CreateSavedReportRequest req,
                                       @AuthenticationPrincipal CachedUser actor) {
        return service.createSavedReport(req, actor.id());
    }
}
