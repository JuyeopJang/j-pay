package juyeop.jpay.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.core.InsufficientFundsException;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.core.SnowflakeIds;
import juyeop.jpay.common.event.UserTransferCompletedEvent;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.dto.TransferRequest;
import juyeop.jpay.payment.dto.TransferResponse;
import juyeop.jpay.payment.entity.UserBalance;
import juyeop.jpay.payment.exception.PaymentErrorType;
import juyeop.jpay.payment.outbox.OutboxEvent;
import juyeop.jpay.payment.outbox.OutboxEventRepository;
import juyeop.jpay.payment.repository.UserBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TransferService {

	private final UserBalanceRepository userBalanceRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	@Transactional
	public TransferResponse transfer(Long fromUserId, TransferRequest request) {
		if (fromUserId.equals(request.toUserId())) {
			throw new BusinessException(PaymentErrorType.TRANSFER_TO_SELF);
		}

		Long firstId = Math.min(fromUserId, request.toUserId());
		Long secondId = Math.max(fromUserId, request.toUserId());

		PaymentErrorType firstError = firstId.equals(fromUserId) ? PaymentErrorType.BALANCE_NOT_FOUND : PaymentErrorType.RECEIVER_NOT_FOUND;
		PaymentErrorType secondError = secondId.equals(fromUserId) ? PaymentErrorType.BALANCE_NOT_FOUND : PaymentErrorType.RECEIVER_NOT_FOUND;

		UserBalance first = userBalanceRepository.findByUserIdForUpdate(firstId)
				.orElseThrow(() -> new BusinessException(firstError));
		UserBalance second = userBalanceRepository.findByUserIdForUpdate(secondId)
				.orElseThrow(() -> new BusinessException(secondError));

		UserBalance sender = firstId.equals(fromUserId) ? first : second;
		UserBalance receiver = sender == first ? second : first;

		try {
			sender.deduct(Money.of(request.amount()));
			receiver.deposit(Money.of(request.amount()));
		} catch (InsufficientFundsException e) {
			throw new BusinessException(PaymentErrorType.INSUFFICIENT_BALANCE);
		}

		long transferId = SnowflakeIds.next();
		outboxEventRepository.save(buildOutboxEvent(transferId, fromUserId, request.toUserId(), request.amount()));

		return new TransferResponse(
				fromUserId,
				request.toUserId(),
				request.amount(),
				sender.getBalance().amount()
		);
	}

	private OutboxEvent buildOutboxEvent(long transferId, long fromUserId, long toUserId, long amount) {
		return OutboxEvent.create(transferId, UserTransferCompletedEvent.TOPIC,
				new UserTransferCompletedEvent(transferId, fromUserId, toUserId, amount, Instant.now()),
				objectMapper);
	}
}