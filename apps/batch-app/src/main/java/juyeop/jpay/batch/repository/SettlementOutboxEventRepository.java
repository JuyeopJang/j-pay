package juyeop.jpay.batch.repository;

import juyeop.jpay.batch.entity.SettlementOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementOutboxEventRepository extends JpaRepository<SettlementOutboxEvent, Long> {

    List<SettlementOutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();
}
