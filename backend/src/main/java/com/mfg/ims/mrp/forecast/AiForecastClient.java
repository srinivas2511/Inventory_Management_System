package com.mfg.ims.mrp.forecast;

import java.io.IOException;
import java.util.List;

import com.mfg.ims.mrp.forecast.ForecastTypes.ForecastPoint;
import com.mfg.ims.mrp.forecast.ForecastTypes.ProductHistory;

/**
 * The call to the external forecasting model. {@link HttpAiForecastClient} is the real one, configured with
 * {@code ims.ai.forecast.url}; tests and demos plug in their own. Anything it returns is validated by the adapter
 * before it is stored, because the model is outside the trust boundary.
 */
public interface AiForecastClient {

    boolean isConfigured();

    /** Host of the configured endpoint, for display only; never the path, credentials or key. */
    String endpointHost();

    List<ForecastPoint> forecast(List<ProductHistory> history, int horizonDays) throws IOException;
}
