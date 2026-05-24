package juyeop.jpay.payment.dto;

import juyeop.jpay.payment.entity.Charge;
import juyeop.jpay.payment.entity.ChargeStatus;

import java.time.Instant;

public record ChargeResponse(
		String chargeId,
		ChargeStatus status,
		Long amount,
		String transferRef,
		String failureReason,
		Instant requestedAt,
		Instant completedAt
) {
	public static ChargeResponse from(Charge charge) {
		return new ChargeResponse(
				String.valueOf(charge.getId()),
				charge.getStatus(),
				charge.getAmount().amount(),
				charge.getTransferRef(),
				charge.getFailureReason(),
				charge.getRequestedAt(),
				charge.getCompletedAt()
		);
	}
}
