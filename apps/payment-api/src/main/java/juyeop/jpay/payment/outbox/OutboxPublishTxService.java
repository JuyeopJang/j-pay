package juyeop.jpay.payment.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxPublishTxService {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void markPublished(Long eventId) {
        outboxEventRepository.findById(eventId)
                .ifPresent(OutboxEvent::markPublished);
    }
}