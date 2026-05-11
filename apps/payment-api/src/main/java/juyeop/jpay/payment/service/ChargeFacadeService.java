package juyeop.jpay.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.web.error.BusinessException;
import juyeop.jpay.payment.dto.ChargeRequest;
import juyeop.jpay.payment.dto.ChargeResponse;
import juyeop.jpay.payment.entity.Charge;
import juyeop.jpay.payment.exception.ChargeErrorType;
import juyeop.jpay.payment.pg.PgClient;
import juyeop.jpay.payment.pg.PgException;
import juyeop.jpay.payment.pg.dto.PgAuthorizeRequest;
import juyeop.jpay.payment.pg.dto.PgAuthorizeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChargeFacadeService {

    private final ObjectMapper objectMapper;
    private final PgClient pgClient;
    private final ChargeService chargeService;

    public ChargeResponse charge(String idempotencyKey, Long userId, ChargeRequest request) {
        return chargeService.findByExternalId(idempotencyKey)
                .map(existing -> replay(existing, userId, request))
                .orElseGet(() -> process(idempotencyKey, userId, request));
    }

    private ChargeResponse replay(Charge existing, Long userId, ChargeRequest request) {
        if (!existing.matches(userId, Money.of(request.amount()), request.paymentMethodId())) {
            throw new BusinessException(ChargeErrorType.IDEMPOTENCY_CONFLICT);
        }
        return ChargeResponse.from(existing);
    }

    private ChargeResponse process(String idempotencyKey, Long userId, ChargeRequest request) {
        Charge pending;
        try {
            pending = chargeService.createPending(
                    idempotencyKey, userId, Money.of(request.amount()), request.paymentMethodId());
        } catch (DataIntegrityViolationException e) {
            // 동시 같은 키 — 다른 트랜잭션이 먼저 INSERT. 재조회 후 replay 분기로.
            Charge existing = chargeService.findByExternalId(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("UNIQUE conflict but row not found"));
            return replay(existing, userId, request);
        }

        try {
            PgAuthorizeResult result = pgClient.authorize(
                    new PgAuthorizeRequest(request.amount(), request.paymentMethodId()));
            Charge updated = applyResult(pending.getId(), result);
            return ChargeResponse.from(updated);
        } catch (PgException e) {
            chargeService.failCharge(pending.getId(), "UPSTREAM_FAILURE", null);
            throw new BusinessException(
                    ChargeErrorType.UPSTREAM_UNAVAILABLE,
                    "PG temporarily unavailable",
                    e);
        }
    }

    private Charge applyResult(Long chargeId, PgAuthorizeResult result) {
        return switch (result) {
            case PgAuthorizeResult.Approved a ->
                    chargeService.completeCharge(chargeId, a.approvalNumber(), toJson(a.meta()));
            case PgAuthorizeResult.Declined d ->
                    chargeService.failCharge(chargeId, d.message(), toJson(d.meta()));
        };
    }

    private String toJson(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize PG meta", e);
        }
    }
}