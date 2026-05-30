package juyeop.jpay.payment.bank.mock;

import juyeop.jpay.payment.bank.BankTransferException;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockRequest;
import juyeop.jpay.payment.bank.mock.dto.BankTransferMockResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 은행 이체 mock HTTP 엔드포인트 — curl 등 수동 테스트용.
 * 내부 호출은 BankTransferClientImpl → BankTransferMockService 직접 호출.
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
@RequiredArgsConstructor
@Slf4j
public class BankTransferMockController {

    private final BankTransferMockService mockService;

    @PostMapping("/transfer")
    public ResponseEntity<BankTransferMockResponse> transfer(@RequestBody BankTransferMockRequest request) {
        try {
            return ResponseEntity.ok(mockService.process(request));
        } catch (BankTransferException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}