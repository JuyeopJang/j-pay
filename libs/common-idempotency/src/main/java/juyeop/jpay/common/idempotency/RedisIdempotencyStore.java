package juyeop.jpay.common.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

@RequiredArgsConstructor
public class RedisIdempotencyStore implements IdempotencyStore {
	private final StringRedisTemplate redisTemplate;

	private static final String KEY_PREFIX = "idempotency:%s";

	@Override
	public Optional<String> load(String key) {
		return Optional.ofNullable(redisTemplate.opsForValue().get(resolveKey(key)));
	}

	@Override
	public void save(String key, String value, Duration ttl) {
		redisTemplate.opsForValue().set(resolveKey(key), value, ttl);
	}

	private String resolveKey(String key) {
		return String.format(KEY_PREFIX, key);
	}
}