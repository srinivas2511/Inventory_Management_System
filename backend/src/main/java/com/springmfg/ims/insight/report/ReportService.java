package com.springmfg.ims.insight.report;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.springmfg.ims.common.exception.NotFoundException;

@Service
public class ReportService {

    private final Map<String, ReportDefinition> definitions;

    public ReportService(List<ReportDefinition> defs) {
        Map<String, ReportDefinition> map = new HashMap<>();
        for (ReportDefinition d : defs) {
            map.put(d.reportCode(), d);
        }
        this.definitions = Collections.unmodifiableMap(map);
    }

    public List<ReportMeta> listReports() {
        return definitions.values().stream()
                .map(d -> new ReportMeta(d.reportCode(), d.displayName()))
                .sorted(Comparator.comparing(ReportMeta::code))
                .toList();
    }

    public ReportResult run(String code, ReportRequest req) {
        ReportDefinition def = definitions.get(code);
        if (def == null) throw new NotFoundException("Report", code);
        List<Map<String, Object>> rows = def.runQuery(req);
        return new ReportResult(def.reportCode(), def.displayName(), def.columnHeaders(), rows, rows.size());
    }

    public record ReportMeta(String code, String name) {}
}
