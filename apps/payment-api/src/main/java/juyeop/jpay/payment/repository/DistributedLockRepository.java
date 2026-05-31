package juyeop.jpay.payment.repository;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class DistributedLockRepository {

    private final RedissonClient redissonClient;

    @Value("${app.redis.key-prefix}")
    private String keyPrefix;

    public boolean tryAcquire(String logicalKey, Duration waitTime, Duration leaseTime) {
        RLock lock = redissonClient.getLock(resolveKey(logicalKey));
        try {
            return lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release(String logicalKey) {
        RLock lock = redissonClient.getLock(resolveKey(logicalKey));
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private String resolveKey(String logicalKey) {
        return keyPrefix + ":" + logicalKey;
    }
}