package juyeop.jpay.batch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "settlement_outbox_events",
        indexes = @Index(name = "idx_settlement_outbox_unpublished", columnList = "published, createdAt")
)
public class SettlementOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String topic;

    // Kafka 파티셔닝 키. 같은 externalId는 항상 같은 파티션으로 라우팅된다.
    @Column(name = "message_key", nullable = false, length = 128)
    private String messageKey;

    // @Lob은 Hibernate 6 + MySQL에서 TINYTEXT(CLOB)로 매핑되어 DDL(TEXT)과 타입 불일치 발생.
    // columnDefinition으로 직접 지정해 검증을 통과시킨다.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SettlementOutboxEvent create(String topic, String messageKey, String payload) {
        SettlementOutboxEvent event = new SettlementOutboxEvent();
        event.topic = topic;
        event.messageKey = messageKey;
        event.payload = payload;
        event.createdAt = Instant.now();
        return event;
    }

    public void markPublished() {
        this.published = true;
    }
}
