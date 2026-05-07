package juyeop.jpay.common.core;

/**
 * 정적 접근자. 애플리케이션 부팅 시 init(nodeId) 호출 후 next()로 ID 발급.
 * Hibernate IdentifierGenerator는 Spring 빈 주입이 어려워 정적 싱글턴으로 우회.
 */
public final class SnowflakeIds {

    private static volatile SnowflakeIdGenerator instance;

    private SnowflakeIds() {
    }

    public static synchronized void init(long nodeId) {
        if (instance != null) {
            throw new IllegalStateException("SnowflakeIds already initialized");
        }
        instance = new SnowflakeIdGenerator(nodeId);
    }

    public static long next() {
        SnowflakeIdGenerator i = instance;
        if (i == null) {
            throw new IllegalStateException(
                    "SnowflakeIds not initialized. Call SnowflakeIds.init(nodeId) at startup.");
        }
        return i.nextId();
    }

    /** 테스트 전용 — 운영 코드에서 호출 금지. */
    static synchronized void reset() {
        instance = null;
    }
}