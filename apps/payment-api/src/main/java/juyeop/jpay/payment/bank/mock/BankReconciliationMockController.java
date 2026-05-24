package juyeop.jpay.payment.bank.mock;

import juyeop.jpay.payment.bank.mock.dto.BankTransactionRecord;
import juyeop.jpay.payment.entity.ChargeStatus;
import juyeop.jpay.payment.repository.ChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 대사(Reconciliation)를 위한 은행 mock 엔드포인트.
 *
 * 왜 필요한가:
 *   실제 운영에서는 은행이 매일 일자별 거래 파일(CSV, XML 등) 또는 API를 통해
 *   "오늘 처리한 이체 내역 전체"를 제공한다. batch-app의 ReconciliationJob은 이를
 *   수신해 내부 기록과 비교한다.
 *   이 프로젝트에서는 실제 은행이 없으므로, payment_db.charges를 읽어
 *   "은행 측 기록"을 흉내낸다. 매직 넘버 기반으로 의도적 불일치를 섞어
 *   대사 Job이 실제로 discrepancy를 탐지할 수 있도록 한다.
 *
 * 불일치 시나리오 (transferRef 마지막 자리 기준):
 *   "0"으로 끝남 → 이 건을 응답에서 누락 → batch-app이 MISSING_IN_BANK 탐지
 *   "1"으로 끝남 → 금액을 +1원 변조해서 응답 → batch-app이 AMOUNT_MISMATCH 탐지
 *   그 외         → 정상 반환
 *
 * 엔드포인트: GET /internal/bank-mock/transactions?date=YYYY-MM-DD
 * 응답: List<BankTransactionRecord>
 */
@RestController
@RequestMapping("/internal/bank-mock")
@RequiredArgsConstructor
public class BankReconciliationMockController {

    private final ChargeRepository chargeRepository;

    @GetMapping("/transactions")
    public List<BankTransactionRecord> transactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        var from = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        var to   = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        return chargeRepository.findByStatusAndCompletedAtBetween(ChargeStatus.COMPLETED, from, to).stream()
                .filter(c -> !c.getTransferRef().endsWith("0"))
                .map(c -> {
                    long amount = c.getTransferRef().endsWith("1")
                            ? c.getAmount().amount() + 1
                            : c.getAmount().amount();
                    return new BankTransactionRecord(
                            c.getTransferRef(), c.getExternalId(), amount, c.getCompletedAt());
                })
                .toList();
    }
}
