package juyeop.jpay.payment.repository;

import juyeop.jpay.payment.entity.Charge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChargeRepository extends JpaRepository<Charge, Long> {
	Optional<Charge> findByExternalId(String externalId);
}
