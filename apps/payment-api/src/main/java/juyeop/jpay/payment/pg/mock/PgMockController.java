package juyeop.jpay.payment.pg.mock;

import juyeop.jpay.payment.pg.mock.dto.PgMockRequest;
import juyeop.jpay.payment.pg.mock.dto.PgMockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * PG mock — 매직 넘버 기반 시나리오 트리거 (Week 2 한정).
 *
 * <pre>
 * amount = 99999 → 200 OK + LIMIT_EXCEEDED 거부
 * amount = 99998 → 5.5초 sleep → client read timeout 유도
 * amount = 99997 → 500 server error
 * 그 외        → 200 OK + PG-{8자 random} 승인
 * </pre>
 *
 * 운영 환경에선 차단 필요 (Week 4 보안 검토 영역).
 */
@RestController
@RequestMapping("/internal/pg-mock")
@Slf4j
public class PgMockController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @PostMapping("/authorize")
    public ResponseEntity<PgMockResponse> authorize(@RequestBody PgMockRequest request) {
        long amount = request.amount();
        long start = System.currentTimeMillis();

        if (amount == 99_999L) {
            return ResponseEntity.ok(new PgMockResponse(
                    null, "LIMIT_EXCEEDED", "카드 한도 초과", null,
                    System.currentTimeMillis() - start));
        }

        if (amount == 99_998L) {
            sleepQuietly(5_500L);
            return ResponseEntity.ok(new PgMockResponse(
                    "PG-LATEZZZZ", null, null, Instant.now(),
                    System.currentTimeMillis() - start));
        }

        if (amount == 99_997L) {
            return ResponseEntity.internalServerError().build();
        }

        String approvalNumber = "PG-" + randomToken(8);
        return ResponseEntity.ok(new PgMockResponse(
                approvalNumber, null, null, Instant.now(),
                System.currentTimeMillis() - start));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}