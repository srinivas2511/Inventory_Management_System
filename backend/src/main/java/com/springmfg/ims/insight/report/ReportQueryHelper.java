package com.springmfg.ims.insight.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportQueryHelper {

    private ReportQueryHelper() {}

    public static List<Map<String, Object>> toMaps(List<?> rows, List<String> headers) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            Object[] cols = (Object[]) row;
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < headers.size() && i < cols.length; i++) {
                map.put(headers.get(i), cols[i]);
            }
            result.add(map);
        }
        return result;
    }
}
