package juyeop.jpay.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.bank.BankTransferClient;
import juyeop.jpay.payment.bank.BankTransferException;
import juyeop.jpay.payment.bank.dto.BankTransferRequest;
import juyeop.jpay.payment.bank.dto.BankTransferResult;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.entity.Charge;
import juyeop.jpay.payment.exception.ChargeErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChargeFacadeService {

	private final ObjectMapper objectMapper;
	private final BankTransferClient bankTransferClient;
	private final ChargeService chargeService;

	public ChargeResponse charge(String idempotencyKey, Long userId, ChargeRequest request) {
		return chargeService.findByExternalId(idempotencyKey)
				.map(existing -> replay(existing, userId, request))
				.orElseGet(() -> process(idempotencyKey, userId, request));
	}

	private ChargeResponse replay(Charge existing, Long userId, ChargeRequest request) {
		if (!existing.matches(userId, Money.of(request.amount()), request.bankAccountId())) {
			throw new BusinessException(ChargeErrorType.IDEMPOTENCY_CONFLICT);
		}
		return ChargeResponse.from(existing);
	}

	private ChargeResponse process(String idempotencyKey, Long userId, ChargeRequest request) {
		Charge pending;
		try {
			pending = chargeService.createPending(
					idempotencyKey, userId, Money.of(request.amount()), request.bankAccountId());
		} catch (DataIntegrityViolationException e) {
			// 동시 같은 키 — 다른 트랜잭션이 먼저 INSERT. 재조회 후 replay 분기로.
			Charge existing = chargeService.findByExternalId(idempotencyKey)
					.orElseThrow(() -> new IllegalStateException("UNIQUE conflict but row not found"));
			return replay(existing, userId, request);
		}

		try {
			BankTransferResult result = bankTransferClient.transfer(
					new BankTransferRequest(request.amount(), pending.getExternalId(), request.bankAccountId()));
			Charge updated = applyResult(pending, result);
			return ChargeResponse.from(updated);
		} catch (BankTransferException e) {
			chargeService.failCharge(pending.getId(), "UPSTREAM_FAILURE", null);
			throw new BusinessException(
					ChargeErrorType.UPSTREAM_UNAVAILABLE,
					"Bank transfer temporarily unavailable",
					e);
		}
	}

	private Charge applyResult(Charge pending, BankTransferResult result) {
		return switch (result) {
			case BankTransferResult.Succeeded a ->
					chargeService.completeWithCreditAndOutbox(pending.getId(), a.transferRef(), toJson(a.meta()));
			case BankTransferResult.Failed d ->
					chargeService.failCharge(pending.getId(), d.message(), toJson(d.meta()));
		};
	}

	private String toJson(Map<String, Object> meta) {
		try {
			return objectMapper.writeValueAsString(meta);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize bank response meta", e);
		}
	}
}
