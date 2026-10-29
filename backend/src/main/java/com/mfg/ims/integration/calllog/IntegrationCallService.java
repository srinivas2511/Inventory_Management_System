package com.mfg.ims.integration.calllog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class IntegrationCallService {

    private final IntegrationCallRepository repo;

    public IntegrationCallService(IntegrationCallRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<IntegrationCallDto> list(String connector) {
        List<IntegrationCall> calls = (connector != null && !connector.isBlank())
                ? repo.findByCompanyIdAndConnectorOrderByCreatedAtDesc(1L, connector)
                : repo.findByCompanyIdOrderByCreatedAtDesc(1L);
        return calls.stream().map(IntegrationCallDto::from).toList();
    }

    public IntegrationCallDto record(String connector, String direction, String requestSummary,
                                     Integer statusCode, Boolean ok, Integer durationMs,
                                     String correlationId, String errorMessage) {
        IntegrationCall call = new IntegrationCall();
        call.setConnector(connector);
        call.setDirection(direction);
        call.setRequestSummary(requestSummary);
        call.setStatusCode(statusCode);
        call.setOk(ok);
        call.setDurationMs(durationMs);
        call.setCorrelationId(correlationId);
        call.setErrorMessage(errorMessage);
        return IntegrationCallDto.from(repo.save(call));
    }
}
