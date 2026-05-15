package juyeop.jpay.common.idempotency;

import java.time.Duration;
import java.util.Optional;

public interface IdempotencyStore {

    Optional<String> load(String key);

    void save(String key, String value, Duration ttl);
}