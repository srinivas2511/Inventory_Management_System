package com.mfg.ims.mrp.forecast;

import java.util.List;

import com.mfg.ims.mrp.forecast.ForecastTypes.ProductHistory;

/** Where the model's training data comes from: dispatched quantity per product per month, oldest first. */
public interface SalesHistorySource {

    List<ProductHistory> history(int months);
}
