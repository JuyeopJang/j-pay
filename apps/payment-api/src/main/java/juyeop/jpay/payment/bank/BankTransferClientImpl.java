package juyeop.jpay.payment.bank;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import juyeop.jpay.payment.bank.dto.BankTransferRequest;
import juyeop.jpay.payment.bank.dto.BankTransferResult;
import juyeop.jpay.payment.bank.mock.BankTransferMockService;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockRequest;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankTransferClientImpl implements BankTransferClient {

    private static final String CB_NAME = "bankTransfer";

    private final BankTransferMockService mockService;

    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME, fallbackMethod = "transferFallback")
    @Override
    public BankTransferResult transfer(BankTransferRequest request) {
        BankTransferMockResponse response = callBank(request);

        if (response.errorCode() != null) {
            return new BankTransferResult.Failed(
                    response.errorCode(),
                    response.message(),
                    toMeta(response));
        }

        if (response.transferRef() != null) {
            return new BankTransferResult.Succeeded(
                    response.transferRef(),
                    toMeta(response));
        }

        throw new BankTransferException("Unexpected bank response: " + response);
    }

    public BankTransferResult transferFallback(BankTransferRequest request, Throwable t) {
        log.warn("Circuit breaker open for bankTransfer, request={}: {}", request.transferId(), t.getMessage());
        throw new BankCircuitOpenException("Bank service unavailable (circuit open)", t);
    }

    private BankTransferMockResponse callBank(BankTransferRequest request) {
        return mockService.process(
                new BankTransferMockRequest(request.amount(), request.transferId(), request.bankAccountId()));
    }

    private Map<String, Object> toMeta(BankTransferMockResponse r) {
        Map<String, Object> m = new HashMap<>();
        if (r.transferRef() != null) m.put("transferRef", r.transferRef());
        if (r.errorCode() != null) m.put("errorCode", r.errorCode());
        if (r.message() != null) m.put("message", r.message());
        if (r.transferredAt() != null) m.put("transferredAt", r.transferredAt().toString());
        if (r.latencyMs() != null) m.put("latencyMs", r.latencyMs());
        return m;
    }
}
