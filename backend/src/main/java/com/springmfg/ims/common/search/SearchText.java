package com.springmfg.ims.common.search;

/** PostgreSQL cannot type a null string inside LOWER/CONCAT, so text filters are bound as "" (= no filter). */
public final class SearchText {

    private SearchText() {
    }

    public static String normalise(String q) {
        return q == null ? "" : q.trim();
    }
}
