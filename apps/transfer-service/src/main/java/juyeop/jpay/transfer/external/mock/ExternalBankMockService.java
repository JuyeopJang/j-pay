package juyeop.jpay.transfer.external.mock;

import juyeop.jpay.transfer.external.ExternalBankException;
import juyeop.jpay.transfer.external.mock.dto.ExternalBankMockRequest;
import juyeop.jpay.transfer.external.mock.dto.ExternalBankMockResponse;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExternalBankMockService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Map<String, ExternalBankMockResponse> RESPONSES = new ConcurrentHashMap<>();

    public ExternalBankMockResponse process(ExternalBankMockRequest request) {
        long amount = request.amount();
        long start = System.currentTimeMillis();

        if (request.transferId() != null) {
            return RESPONSES.computeIfAbsent(String.valueOf(request.transferId()), key -> generate(amount, start));
        }

        return generate(amount, start);
    }

    private ExternalBankMockResponse generate(long amount, long start) {
        if (amount == 99_999L) {
            return new ExternalBankMockResponse(
                    null, "ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다", null,
                    System.currentTimeMillis() - start);
        }
        if (amount == 99_998L) {
            // CB slow-call 시뮬레이션 — slow-call-duration-threshold(3s) 초과
            sleepQuietly(5_500L);
            return new ExternalBankMockResponse(
                    "TRANSFER-LATE", null, null, Instant.now(),
                    System.currentTimeMillis() - start);
        }
        if (amount == 99_997L) {
            throw new ExternalBankException("External bank 5xx: 500");
        }
        // 실제 외부 API 네트워크 지연 시뮬레이션
        sleepQuietly(200L);
        return new ExternalBankMockResponse(
                "REMIT-" + randomToken(8), null, null, Instant.now(),
                System.currentTimeMillis() - start);
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