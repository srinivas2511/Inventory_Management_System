package com.springmfg.ims.mrp.forecast;

import java.util.List;

import com.springmfg.ims.mrp.forecast.ForecastTypes.ForecastPoint;

/**
 * Where forecasts come from. The default ({@link ManualDemandForecastProvider}) returns what planners entered by
 * hand; {@link AiForecastAdapter} asks an external model. Both feed the same {@code demand_forecasts} table that MRP
 * already reads (ANA-003).
 */
public interface DemandForecastProvider {

    String name();

    List<ForecastPoint> forecast(int horizonDays);
}
