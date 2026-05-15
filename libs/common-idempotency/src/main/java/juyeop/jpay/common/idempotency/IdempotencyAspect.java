package juyeop.jpay.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotencyAspect {

	private final ObjectMapper objectMapper;
	private final IdempotencyStore idempotencyStore;

	@Around("@annotation(idempotent)")
	public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
		String idempotencyKey = request.getHeader("Idempotency-Key");

		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return pjp.proceed();
		}

		try {
			Optional<String> cached = idempotencyStore.load(idempotencyKey);

			if (cached.isPresent()) {
				Class<?> returnType = ((MethodSignature) pjp.getSignature()).getReturnType();
				return objectMapper.readValue(cached.get(), returnType);
			}
		} catch (Exception e) {
			log.warn("idempotencyStore.load() failed: {}", e.getMessage(), e);
		}

		Object result = pjp.proceed();

		try {
			idempotencyStore.save(idempotencyKey, objectMapper.writeValueAsString(result), Duration.ofHours(idempotent.ttlHours()));
		} catch (Exception e) {
			log.warn("idempotencyStore.save() failed: {}", e.getMessage(), e);
		}

		return result;
	}
}