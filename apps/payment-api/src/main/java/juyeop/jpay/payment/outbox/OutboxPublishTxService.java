package juyeop.jpay.payment.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxPublishTxService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void markPublishedBatch(List<Long> eventIds) {
        outboxEventRepository.markPublishedBatch(eventIds);
    }
}