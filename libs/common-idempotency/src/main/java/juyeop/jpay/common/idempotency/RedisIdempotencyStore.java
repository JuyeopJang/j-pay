package juyeop.jpay.common.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisIdempotencyStore implements IdempotencyStore {
	private final StringRedisTemplate redisTemplate;
	private final String keyPrefix;

	public RedisIdempotencyStore(StringRedisTemplate redisTemplate, String keyPrefix) {
		this.redisTemplate = redisTemplate;
		this.keyPrefix = keyPrefix;
	}

	@Override
	public Optional<String> load(String key) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(resolveKey(key)));
	}

	@Override
	public void save(String key, String value, Duration ttl) {
		redisTemplate.opsForValue().set(resolveKey(key), value, ttl);
	}

	private String resolveKey(String key) {
		return keyPrefix + key;
	}
}