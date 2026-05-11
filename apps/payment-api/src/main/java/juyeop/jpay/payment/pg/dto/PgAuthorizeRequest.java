package juyeop.jpay.payment.pg.dto;

public record PgAuthorizeRequest(
        long amount,
        String cardToken
) {
}