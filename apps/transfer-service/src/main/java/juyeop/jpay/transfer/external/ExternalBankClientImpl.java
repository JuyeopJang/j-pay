package juyeop.jpay.transfer.external;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import juyeop.jpay.transfer.external.dto.ExternalBankTransferRequest;
import juyeop.jpay.transfer.external.dto.ExternalBankTransferResult;
import juyeop.jpay.transfer.external.mock.ExternalBankMockService;
import juyeop.jpay.transfer.external.mock.dto.ExternalBankMockRequest;
import juyeop.jpay.transfer.external.mock.dto.ExternalBankMockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ExternalBankClientImpl implements ExternalBankClient {

    private static final String CB_NAME = "externalBank";

    private final ExternalBankMockService mockService;

    public ExternalBankClientImpl(ExternalBankMockService mockService) {
        this.mockService = mockService;
    }

    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "transferFallback")
    @Override
    public ExternalBankTransferResult transfer(ExternalBankTransferRequest request) {
        ExternalBankMockResponse response = callBank(request);

        if (response == null) {
            throw new ExternalBankException("External bank returned null body");
        }

        if (response.errorCode() != null) {
            return new ExternalBankTransferResult.Failed(
                    response.errorCode(),
                    response.message(),
                    toMeta(response));
        }

        if (response.transferRef() != null) {
            return new ExternalBankTransferResult.Succeeded(
                    response.transferRef(),
                    toMeta(response));
        }

        throw new ExternalBankException("Unexpected external bank response: " + response);
    }

    public ExternalBankTransferResult transferFallback(ExternalBankTransferRequest request, Throwable t) {
        log.warn("Circuit breaker open for externalBank: {}", t.getMessage());
        throw new ExternalBankCircuitOpenException("External bank unavailable (circuit open)", t);
    }

    private ExternalBankMockResponse callBank(ExternalBankTransferRequest request) {
        try {
            return mockService.process(
                    new ExternalBankMockRequest(request.transferId(), request.bankAccountId(), request.amount()));
        } catch (ExternalBankException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalBankException("External bank call failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> toMeta(ExternalBankMockResponse r) {
        Map<String, Object> m = new HashMap<>();
        if (r.transferRef() != null) m.put("transferRef", r.transferRef());
        if (r.errorCode() != null) m.put("errorCode", r.errorCode());
        if (r.message() != null) m.put("message", r.message());
        if (r.transferredAt() != null) m.put("transferredAt", r.transferredAt().toString());
        if (r.latencyMs() != null) m.put("latencyMs", r.latencyMs());
        return m;
    }
}