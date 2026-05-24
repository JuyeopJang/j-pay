package juyeop.jpay.transfer.external.mock;

import juyeop.jpay.transfer.external.mock.dto.ExternalBankMockRequest;
import juyeop.jpay.transfer.external.mock.dto.ExternalBankMockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * 외부 은행(정산 송금) mock — 매직 넘버 기반 시나리오 트리거.
 *
 * <pre>
 * amount = 99999 → ACCOUNT_NOT_FOUND (계좌 없음)
 * amount = 99998 → 5.5초 sleep → client read timeout 유도 → CB failure 누적
 * amount = 99997 → 500 server error → CB failure 누적
 * 그 외        → TRANSFER-{8자 random} 성공
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/mock/external-bank")
public class ExternalBankMockController {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @PostMapping("/transfer")
    public ResponseEntity<ExternalBankMockResponse> transfer(@RequestBody ExternalBankMockRequest request) {
        long amount = request.amount();
        long start = System.currentTimeMillis();

        if (amount == 99_999L) {
            return ResponseEntity.ok(new ExternalBankMockResponse(
                    null, "ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다", null,
                    System.currentTimeMillis() - start));
        }

        if (amount == 99_998L) {
            sleepQuietly(5_500L);
            return ResponseEntity.ok(new ExternalBankMockResponse(
                    "TRANSFER-LATE", null, null, Instant.now(),
                    System.currentTimeMillis() - start));
        }

        if (amount == 99_997L) {
            return ResponseEntity.internalServerError().build();
        }

        String ref = "REMIT-" + randomToken(8);
        return ResponseEntity.ok(new ExternalBankMockResponse(
                ref, null, null, Instant.now(),
                System.currentTimeMillis() - start));
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
