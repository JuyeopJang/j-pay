package juyeop.jpay.payment.pg;

import juyeop.jpay.payment.pg.dto.PgAuthorizeRequest;
import juyeop.jpay.payment.pg.dto.PgAuthorizeResult;

/**
 * 외부 PG와의 통신 추상화. 비즈니스 의미 있는 결과는 PgAuthorizeResult,
 * 시스템 통신 실패는 PgException으로 분리.
 */
public interface PgClient {

    PgAuthorizeResult authorize(PgAuthorizeRequest request);
}
