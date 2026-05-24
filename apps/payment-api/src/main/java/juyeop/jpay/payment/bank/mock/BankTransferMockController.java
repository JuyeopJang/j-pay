package juyeop.jpay.payment.bank.mock;

import juyeop.jpay.payment.bank.mock.dto.BankTransferMockRequest;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 은행 이체 mock — 매직 넘버 기반 시나리오 트리거.
 *
 * <pre>
 * amount = 99999 → 200 OK + INSUFFICIENT_BALANCE 거부
 * amount = 99998 → 5.5초 sleep → client read timeout 유도
 * amount = 99997 → 500 server error
 * 그 외 → 200 OK + TRANSFER-{8자 random} 성공
 * </pre>
 */
@RestController
@RequestMapping("/internal/bank-mock")
@Slf4j
public class BankTransferMockController {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final Map<String, BankTransferMockResponse> RESPONSES = new ConcurrentHashMap<>();

	@PostMapping("/transfer")
	public ResponseEntity<BankTransferMockResponse> transfer(@RequestBody BankTransferMockRequest request) {
		long amount = request.amount();
		long start = System.currentTimeMillis();

		if (amount == 99_997L) {
			return ResponseEntity.internalServerError().build();
		}

		if (request.transferId() != null) {
			return ResponseEntity.ok(RESPONSES.computeIfAbsent(request.transferId(), key -> generate(amount, start)));
		}

		return ResponseEntity.ok(generate(amount, start));
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