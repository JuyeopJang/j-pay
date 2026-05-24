package juyeop.jpay.payment.bank;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import juyeop.jpay.payment.bank.dto.BankTransferRequest;
import juyeop.jpay.payment.bank.dto.BankTransferResult;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockRequest;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class BankTransferClientImpl implements BankTransferClient {

	private static final String CB_NAME = "bankTransfer";

	private final RestClient restClient;

	public BankTransferClientImpl(RestClient bankTransferRestClient) {
		this.restClient = bankTransferRestClient;
	}

	@Retry(name = CB_NAME)
	@CircuitBreaker(name = CB_NAME)
	@Override
	public BankTransferResult transfer(BankTransferRequest request) {
		BankTransferMockResponse response = callBank(request);

		if (response == null) {
			throw new BankTransferException("Bank returned null body");
		}

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

	private BankTransferMockResponse callBank(BankTransferRequest request) {
		try {
			return restClient.post()
					.uri("/internal/bank-mock/transfer")
					.body(new BankTransferMockRequest(request.amount(), request.transferId(), request.bankAccountId()))
					.retrieve()
					.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
						throw new BankTransferException("Bank 5xx: " + res.getStatusCode().value());
					})
					.body(BankTransferMockResponse.class);
		} catch (BankTransferException e) {
			throw e;
		} catch (Exception e) {
			throw new BankTransferException("Bank call failed: " + e.getMessage(), e);
		}
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