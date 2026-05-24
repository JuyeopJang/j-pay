package juyeop.jpay.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.payment.bank.BankTransferClient;
import juyeop.jpay.payment.bank.BankTransferException;
import juyeop.jpay.payment.bank.dto.BankTransferRequest;
import juyeop.jpay.payment.bank.dto.BankTransferResult;
import juyeop.jpay.payment.entity.Charge;
import juyeop.jpay.payment.entity.ChargeStatus;
import juyeop.jpay.payment.repository.ChargeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class PendingChargeRecoveryScheduler {

    // 정상 충전 처리 시간(< 10초)보다 충분히 길게 설정 — 3분이면 정상 처리 중인 건과 구분 가능
    private static final Duration RECOVERY_THRESHOLD = Duration.ofMinutes(3);

    private final ChargeRepository chargeRepository;
    private final BankTransferClient bankTransferClient;
    private final ChargeService chargeService;
    private final ObjectMapper objectMapper;

    // fixedDelay: 이전 실행 완료 후 1분 대기. fixedRate는 실행이 길어지면 중복 실행 가능.
    @Scheduled(fixedDelay = 60_000)
    public void recover() {
        Instant threshold = Instant.now().minus(RECOVERY_THRESHOLD);
        List<Charge> candidates = chargeRepository.findByStatusAndRequestedAtBefore(ChargeStatus.PENDING, threshold);

        for (Charge charge : candidates) {
            try {
                recoverCharge(charge);
            } catch (Exception e) {
                log.warn("charge recovery failed: chargeId={}", charge.getId(), e);
            }
        }
    }

    private void recoverCharge(Charge charge) {
        BankTransferResult result;
        try {
            result = bankTransferClient.transfer(
                    new BankTransferRequest(charge.getAmount().amount(), charge.getExternalId(), charge.getBankAccountId()));
        } catch (BankTransferException e) {
            // 은행 API 자체가 불안정 — 이번 사이클은 건너뛰고 다음에 재시도
            log.warn("bank API unavailable during recovery, skip: chargeId={}", charge.getId(), e);
            return;
        }

        switch (result) {
            case BankTransferResult.Succeeded s ->
                    chargeService.completeWithCreditAndOutbox(charge.getId(), s.transferRef(), toJson(s.meta()));
            case BankTransferResult.Failed f ->
                    chargeService.failCharge(charge.getId(), f.message(), toJson(f.meta()));
        }
    }

    private String toJson(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize meta", e);
        }
    }
}
