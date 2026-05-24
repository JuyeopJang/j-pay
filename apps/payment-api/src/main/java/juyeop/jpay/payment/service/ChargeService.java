package juyeop.jpay.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.event.ChargeCompletedEvent;
import juyeop.jpay.payment.entity.Charge;
import juyeop.jpay.payment.exception.BalanceNotFoundException;
import juyeop.jpay.payment.outbox.OutboxEvent;
import juyeop.jpay.payment.outbox.OutboxEventRepository;
import juyeop.jpay.payment.repository.ChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChargeService {
	private final ChargeRepository chargeRepository;
	private final UserBalanceTxService userBalanceTxService;
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	@Transactional(readOnly = true)
	public Optional<Charge> findByExternalId(String externalId) {
		return chargeRepository.findByExternalId(externalId);
	}

	@Transactional
	public Charge createPending(String externalId, Long userId, Money amount, String bankAccountId) {
		Charge charge = Charge.pending(externalId, userId, amount, bankAccountId);
		return chargeRepository.save(charge);
	}

	@Transactional
	public Charge failCharge(Long chargeId, String failureReason, String bankResponseMeta) {
		Charge charge = chargeRepository.findById(chargeId).orElseThrow(() -> new IllegalStateException("Charge not found: " + chargeId));
		charge.fail(failureReason, bankResponseMeta);
		return charge;
	}

	@Retryable(retryFor = DataAccessException.class,
			noRetryFor = {IllegalStateException.class, BalanceNotFoundException.class},
			maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000))
	@Transactional
	public Charge completeWithCreditAndOutbox(Long chargeId, String transferRef, String bankResponseMeta) {
		Charge charge = chargeRepository.findById(chargeId).orElseThrow(() -> new IllegalStateException("Charge not found: " + chargeId));
		charge.complete(transferRef, bankResponseMeta);
		userBalanceTxService.deposit(charge.getUserId(), charge.getAmount());
		outboxEventRepository.save(buildOutboxEvent(charge));
		return charge;
	}

	private OutboxEvent buildOutboxEvent(Charge charge) {
		ChargeCompletedEvent event = new ChargeCompletedEvent(
				charge.getId(), charge.getUserId(), charge.getAmount().amount(), Instant.now());
		try {
			return OutboxEvent.create(charge.getId(), ChargeCompletedEvent.TOPIC,
					objectMapper.writeValueAsString(event));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
