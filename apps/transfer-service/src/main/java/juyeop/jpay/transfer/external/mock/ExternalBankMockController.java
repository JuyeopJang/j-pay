package juyeop.jpay.transfer.external.mock;

import juyeop.jpay.transfer.external.mock.dto.ExternalBankMockRequest;
import juyeop.jpay.transfer.external.mock.dto.ExternalBankMockResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 외부 은행(정산 송금) mock — curl 등 수동 테스트용.
 * 내부 호출은 ExternalBankClientImpl → ExternalBankMockService 직접 호출.
 *
 * <pre>
 * amount = 99999 → ACCOUNT_NOT_FOUND
 * amount = 99998 → 5.5초 sleep → CB slow-call 누적
 * amount = 99997 → 500 server error → CB failure 누적
 * 그 외          → REMIT-{8자 random} 성공
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/mock/external-bank")
@RequiredArgsConstructor
public class ExternalBankMockController {

    private final ExternalBankMockService mockService;

    @PostMapping("/transfer")
    public ResponseEntity<ExternalBankMockResponse> transfer(@RequestBody ExternalBankMockRequest request) {
        try {
            return ResponseEntity.ok(mockService.process(request));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}