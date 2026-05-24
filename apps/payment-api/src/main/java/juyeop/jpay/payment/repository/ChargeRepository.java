package juyeop.jpay.payment.repository;

import juyeop.jpay.payment.entity.Charge;
import juyeop.jpay.payment.entity.ChargeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChargeRepository extends JpaRepository<Charge, Long> {

	Optional<Charge> findByExternalId(String externalId);

	List<Charge> findByStatusAndCompletedAtBetween(ChargeStatus status, Instant from, Instant to);

	List<Charge> findByStatusAndRequestedAtBefore(ChargeStatus status, Instant threshold);
}
