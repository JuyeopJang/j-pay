package juyeop.jpay.common.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("nodeId 음수면 IllegalArgumentException (경계 직하)")
    void reject_negative_node_id() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nodeId 1024 (MAX+1)면 IllegalArgumentException (10bit 경계 직상)")
    void reject_node_id_above_max() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("nodeId 0 (MIN)은 허용 (경계 하단)")
    void accept_min_node_id() {
        SnowflakeIdGenerator g = new SnowflakeIdGenerator(0L);
        assertThat(g.nextId()).isPositive();
    }

    @Test
    @DisplayName("nodeId 1023 (MAX)은 허용 (10bit 경계 상단)")
    void accept_max_node_id() {
        SnowflakeIdGenerator g = new SnowflakeIdGenerator(1023L);
        assertThat(g.nextId()).isPositive();
    }

    @Test
    @DisplayName("연속 호출 시 ID는 단조 증가")
    void ids_are_monotonic() {
        SnowflakeIdGenerator g = new SnowflakeIdGenerator(1L);
        long prev = g.nextId();
        for (int i = 0; i < 1_000; i++) {
            long curr = g.nextId();
            assertThat(curr).isGreaterThan(prev);
            prev = curr;
        }
    }

    @Test
    @DisplayName("1만개 ID는 모두 unique")
    void ten_thousand_ids_are_unique() {
        SnowflakeIdGenerator g = new SnowflakeIdGenerator(1L);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(g.nextId());
        }
        assertThat(ids).hasSize(10_000);
    }

    @Test
    @DisplayName("멀티스레드 8 × 1000 = 8000개 ID도 unique (sequence 충돌 검증)")
    void multi_thread_ids_are_unique() throws Exception {
        SnowflakeIdGenerator g = new SnowflakeIdGenerator(1L);
        int threads = 8;
        int idsPerThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<Long> all = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < idsPerThread; i++) {
                        all.add(g.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(all).hasSize(threads * idsPerThread);
    }

    @Test
    @DisplayName("nodeId가 다르면 같은 ms에서도 ID 충돌 없음")
    void different_node_ids_dont_collide() {
        SnowflakeIdGenerator g1 = new SnowflakeIdGenerator(1L);
        SnowflakeIdGenerator g2 = new SnowflakeIdGenerator(2L);

        Set<Long> all = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            all.add(g1.nextId());
            all.add(g2.nextId());
        }
        assertThat(all).hasSize(2_000);
    }
}