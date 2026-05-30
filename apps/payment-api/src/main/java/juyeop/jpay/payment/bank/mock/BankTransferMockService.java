package juyeop.jpay.payment.bank.mock;

import juyeop.jpay.payment.bank.BankTransferException;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockRequest;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockResponse;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BankTransferMockService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final Map<String, BankTransferMockResponse> RESPONSES = new ConcurrentHashMap<>();

    public BankTransferMockResponse process(BankTransferMockRequest request) {
        long amount = request.amount();
        long start = System.currentTimeMillis();

        if (amount == 99_997L) {
            throw new BankTransferException("Bank 5xx: 500");
        }

        if (request.transferId() != null) {
            return RESPONSES.computeIfAbsent(request.transferId(), key -> generate(amount, start));
        }

        return generate(amount, start);
    }

    private BankTransferMockResponse generate(long amount, long start) {
        if (amount == 99_999L) {
            return new BankTransferMockResponse(null, "INSUFFICIENT_BALANCE", "계좌 잔액 부족", null,
                    System.currentTimeMillis() - start);
        }
        if (amount == 99_998L) {
            sleepQuietly(5_500L);
            return new BankTransferMockResponse("TRANSFER-LATEZZZZ", null, null, Instant.now(),
                    System.currentTimeMillis() - start);
        }
        // 실제 외부 API 네트워크 지연 시뮬레이션
        sleepQuietly(200L);
        return new BankTransferMockResponse("TRANSFER-" + randomToken(8), null, null, Instant.now(),
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
