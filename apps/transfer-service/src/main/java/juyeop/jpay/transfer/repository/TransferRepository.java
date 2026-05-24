package juyeop.jpay.transfer.repository;

import juyeop.jpay.transfer.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByExternalId(String externalId);
}
