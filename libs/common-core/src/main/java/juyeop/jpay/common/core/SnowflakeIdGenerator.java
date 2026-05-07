package juyeop.jpay.common.core;

/**
 * Twitter Snowflake 스타일 64bit ID 생성기.
 *
 * <pre>
 *  | 1bit unused | 41bit timestamp | 10bit nodeId | 12bit sequence |
 * </pre>
 *
 * - timestamp: epoch(2024-01-01 UTC) 이후 ms. 41bit로 약 69년 표현
 * - nodeId: 0 ~ 1023 (1024개 노드 운용 가능)
 * - sequence: 같은 ms 내 0 ~ 4095 (4096개/ms = 약 4M TPS 상한)
 */
public final class SnowflakeIdGenerator {

    /** 2024-01-01 00:00:00 UTC. 표현 가능 시간을 더 미래로 밀기 위한 커스텀 epoch. */
    private static final long EPOCH_MILLIS = 1_704_067_200_000L;

    private static final long NODE_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;

    public  static final long MAX_NODE_ID  = (1L << NODE_ID_BITS) - 1;   // 1023
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;  // 4095

    private static final long NODE_ID_SHIFT   = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;

    private final long nodeId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                    "nodeId must be in [0, " + MAX_NODE_ID + "], got: " + nodeId);
        }
        this.nodeId = nodeId;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new IllegalStateException(
                    "Clock moved backwards. lastTimestamp=" + lastTimestamp +
                    ", current=" + timestamp);
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0L) {
                // sequence 소진 → 다음 ms까지 busy-wait
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - EPOCH_MILLIS) << TIMESTAMP_SHIFT)
                | (nodeId << NODE_ID_SHIFT)
                | sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTs) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}