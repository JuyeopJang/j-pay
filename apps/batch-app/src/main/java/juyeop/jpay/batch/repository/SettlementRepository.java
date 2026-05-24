package juyeop.jpay.batch.repository;

import juyeop.jpay.batch.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findByMerchantIdAndPeriodStartAndPeriodEnd(
            String merchantId, LocalDate periodStart, LocalDate periodEnd);

    Optional<Settlement> findByTransferExternalId(String transferExternalId);
}
