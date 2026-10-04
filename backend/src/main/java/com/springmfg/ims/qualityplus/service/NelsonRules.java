package com.springmfg.ims.qualityplus.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Nelson rules 1 and 2 (QMS-007), evaluated for the newest plotted point. */
public final class NelsonRules {

    public static final String RULE_1 = "NELSON_R1";
    public static final String RULE_2 = "NELSON_R2";

    private NelsonRules() {
    }

    /**
     * @param history plotted statistics oldest first, the point being judged last
     * @param runLength consecutive points on one side of the centre line that signal a shift (rule 2)
     * @return the violated rules joined by commas, or null when the point is in control
     */
    public static String evaluate(List<BigDecimal> history, BigDecimal cl, BigDecimal ucl, BigDecimal lcl,
                                  int runLength) {
        if (history.isEmpty() || cl == null) {
            return null;
        }
        List<String> hit = new ArrayList<>();
        BigDecimal last = history.get(history.size() - 1);
        if ((ucl != null && last.compareTo(ucl) > 0) || (lcl != null && last.compareTo(lcl) < 0)) {
            hit.add(RULE_1);
        }
        if (history.size() >= runLength) {
            boolean above = true;
            boolean below = true;
            for (BigDecimal v : history.subList(history.size() - runLength, history.size())) {
                above &= v.compareTo(cl) > 0;
                below &= v.compareTo(cl) < 0;
            }
            if (above || below) {
                hit.add(RULE_2);
            }
        }
        return hit.isEmpty() ? null : String.join(",", hit);
    }
}
