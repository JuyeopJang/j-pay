package juyeop.jpay.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class IdempotencyConfig {

    @Bean
    public IdempotencyStore idempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyStore(redisTemplate);
    }

    @Bean
    public IdempotencyAspect idempotencyAspect(ObjectMapper objectMapper, IdempotencyStore idempotencyStore) {
        return new IdempotencyAspect(objectMapper, idempotencyStore);
    }
}