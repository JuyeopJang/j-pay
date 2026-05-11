package juyeop.jpay.payment.pg.dto;

import java.util.Map;

/**
 * PG 호출 결과. 승인/거부 두 케이스를 sealed로 분리해 호출자가 두 케이스 모두 다루도록 컴파일 단에서 강제.
 *
 * <pre>
 * switch (pgClient.authorize(req)) {
 *     case PgAuthorizeResult.Approved a -> charge.markCompleted(a.approvalNumber(), a.meta());
 *     case PgAuthorizeResult.Declined d -> charge.markFailed(d.message(), d.meta());
 * }
 * </pre>
 */
public sealed interface PgAuthorizeResult
        permits PgAuthorizeResult.Approved, PgAuthorizeResult.Declined {

    /** PG 승인 성공. */
    record Approved(
            String approvalNumber,
            Map<String, Object> meta
    ) implements PgAuthorizeResult {
    }

    /** PG 비즈니스 거절 (카드 한도 초과, 카드 정지 등). */
    record Declined(
            String errorCode,
            String message,
            Map<String, Object> meta
    ) implements PgAuthorizeResult {
    }
}