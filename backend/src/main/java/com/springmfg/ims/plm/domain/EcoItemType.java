package com.springmfg.ims.plm.domain;

/** What an affected item changes. Only BOM items create a new revision on implementation; the others are recorded. */
public enum EcoItemType {
    PRODUCT, BOM, ROUTING
}
