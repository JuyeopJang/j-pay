package juyeop.jpay.payment.pg;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import juyeop.jpay.payment.pg.dto.PgAuthorizeRequest;
import juyeop.jpay.payment.pg.dto.PgAuthorizeResult;
import juyeop.jpay.payment.pg.mock.dto.PgMockRequest;
import juyeop.jpay.payment.pg.mock.dto.PgMockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * PG 호출 구현 — RestClient + Resilience4j (Retry/CircuitBreaker).
 *
 * 어노테이션 적용 순서 (Spring AOP 기본):
 *   @Retry (outermost) → @CircuitBreaker → method
 * 즉 retry 시도마다 CB가 호출 횟수를 셈.
 */
@Component
@Slf4j
public class PgClientImpl implements PgClient {

    private static final String CB_NAME = "pgAuthorize";

    private final RestClient restClient;

    public PgClientImpl(RestClient pgRestClient) {
        this.restClient = pgRestClient;
    }

    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME)
    @Override
    public PgAuthorizeResult authorize(PgAuthorizeRequest request) {
        PgMockResponse response = callPg(request);

        if (response == null) {
            throw new PgException("PG returned null body");
        }

        if (response.errorCode() != null) {
            return new PgAuthorizeResult.Declined(
                    response.errorCode(),
                    response.message(),
                    toMeta(response));
        }

        if (response.approvalNumber() != null) {
            return new PgAuthorizeResult.Approved(
                    response.approvalNumber(),
                    toMeta(response));
        }

        throw new PgException("Unexpected PG response: " + response);
    }

    private PgMockResponse callPg(PgAuthorizeRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/pg-mock/authorize")
                    .body(new PgMockRequest(request.amount(), request.cardToken()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new PgException("PG 5xx: " + res.getStatusCode().value());
                    })
                    .body(PgMockResponse.class);
        } catch (PgException e) {
            throw e;
        } catch (Exception e) {
            throw new PgException("PG call failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> toMeta(PgMockResponse r) {
        Map<String, Object> m = new HashMap<>();
        if (r.approvalNumber() != null) m.put("approvalNumber", r.approvalNumber());
        if (r.errorCode() != null) m.put("errorCode", r.errorCode());
        if (r.message() != null) m.put("message", r.message());
        if (r.approvedAt() != null) m.put("approvedAt", r.approvedAt().toString());
        if (r.latencyMs() != null) m.put("latencyMs", r.latencyMs());
        return m;
    }
}