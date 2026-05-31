package juyeop.jpay.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "outbox_events",
        indexes = @Index(name = "idx_outbox_unpublished", columnList = "published, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String topic;

    // @Lob은 Hibernate 6 + MySQL에서 TINYTEXT(CLOB)로 매핑되어 schema-validation 실패. columnDefinition으로 직접 지정.
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public static OutboxEvent create(Long id, String topic, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.id = id;
        event.topic = topic;
        event.payload = payload;
        event.createdAt = Instant.now();
        return event;
    }

    public static OutboxEvent create(Long id, String topic, Object eventPayload, ObjectMapper mapper) {
        try {
            return create(id, topic, mapper.writeValueAsString(eventPayload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event: topic=" + topic, e);
        }
    }

    public void markPublished() {
        this.published = true;
    }
}