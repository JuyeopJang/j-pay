package juyeop.jpay.payment.service;

import juyeop.jpay.common.core.Money;
import juyeop.jpay.payment.entity.Charge;
import juyeop.jpay.payment.repository.ChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChargeService {
	private final ChargeRepository chargeRepository;

	@Transactional(readOnly = true)
	public Optional<Charge> findByExternalId(String externalId) {
		return chargeRepository.findByExternalId(externalId);
	}

	@Transactional
	public Charge createPending(
			String externalId,
			Long userId,
			Money amount,
			String paymentMethodId
	) {
		Charge charge = Charge.pending(externalId, userId, amount, paymentMethodId);
		return chargeRepository.save(charge);
	}

	@Transactional
	public Charge completeCharge(Long chargeId, String approvalNumber, String pgResponseMeta) {
		Charge charge = chargeRepository.findById(chargeId)
				.orElseThrow(() -> new IllegalStateException("Charge not found: " + chargeId));
		charge.complete(approvalNumber, pgResponseMeta);
		return charge;
	}

	@Transactional
	public Charge failCharge(Long chargeId, String failureReason, String pgResponseMeta) {
		Charge charge = chargeRepository.findById(chargeId)
				.orElseThrow(() -> new IllegalStateException("Charge not found: " + chargeId));
		charge.fail(failureReason, pgResponseMeta);
		return charge;
	}
}
