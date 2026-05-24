package juyeop.jpay.batch.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "payment_count", nullable = false)
    private int paymentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(name = "transfer_external_id")
    private String transferExternalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Settlement of(String merchantId, LocalDate periodStart, LocalDate periodEnd,
                                long totalAmount, int paymentCount, String transferExternalId) {
        Settlement s = new Settlement();
        s.merchantId = merchantId;
        s.periodStart = periodStart;
        s.periodEnd = periodEnd;
        s.totalAmount = totalAmount;
        s.paymentCount = paymentCount;
        s.transferExternalId = transferExternalId;
        s.status = SettlementStatus.PENDING;
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void markTransferred() {
        this.status = SettlementStatus.TRANSFERRED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = SettlementStatus.FAILED;
        this.updatedAt = Instant.now();
    }
}
