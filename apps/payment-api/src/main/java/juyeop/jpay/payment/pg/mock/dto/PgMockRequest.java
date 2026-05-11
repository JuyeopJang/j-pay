package juyeop.jpay.payment.pg.mock.dto;

public record PgMockRequest(
        long amount,
        String cardToken
) {
}