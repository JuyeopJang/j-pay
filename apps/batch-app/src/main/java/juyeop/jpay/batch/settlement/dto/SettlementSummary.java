package juyeop.jpay.batch.settlement.dto;

public record SettlementSummary(
        String merchantId,
        long totalAmount,
        int paymentCount
) {}
