package com.springmfg.ims.qualityplus.domain;

/** Chart types implemented in R16 (QMS-007): X-bar/R for measured values, P for proportion defective, C for defect counts. */
public enum SpcChartType {
    XBAR_R, P, C;

    public boolean isAttribute() {
        return this != XBAR_R;
    }
}
