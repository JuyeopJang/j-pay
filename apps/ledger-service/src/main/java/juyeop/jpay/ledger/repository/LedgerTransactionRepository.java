package juyeop.jpay.ledger.repository;

import juyeop.jpay.ledger.entity.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    // 멱등성 체크용. paymentId(String)을 external_id로 사용한다.
    // Kafka at-least-once로 같은 paymentId 이벤트가 두 번 올 수 있으므로
    // 처리 전 반드시 확인해야 한다.
    boolean existsByExternalId(String externalId);
}