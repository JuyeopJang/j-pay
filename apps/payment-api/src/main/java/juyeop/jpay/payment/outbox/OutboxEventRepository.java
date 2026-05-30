package juyeop.jpay.payment.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop500ByPublishedFalseOrderByCreatedAtAsc();

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.published = true WHERE e.id IN :ids")
    void markPublishedBatch(@Param("ids") List<Long> ids);
}