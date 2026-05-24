package juyeop.jpay.batch.settlement;

import juyeop.jpay.batch.repository.SettlementOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementOutboxPublishTxService {

    private final SettlementOutboxEventRepository repository;

    // Kafka 발행 성공 후 별도 트랜잭션으로 커밋한다.
    // 발행과 markPublished를 같은 트랜잭션으로 묶으면 Kafka 발행이 롤백 대상이 되지 않아
    // DB 커밋 전에 Kafka에 이미 메시지가 들어가는 순서 문제가 생길 수 있다.
    @Transactional
    public void markPublished(Long eventId) {
        repository.findById(eventId).ifPresent(e -> e.markPublished());
    }
}
