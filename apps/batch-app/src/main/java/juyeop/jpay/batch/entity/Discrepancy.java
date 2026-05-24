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
@Table(name = "discrepancies")
public class Discrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reconciliation_date", nullable = false)
    private LocalDate reconciliationDate;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "transfer_ref")
    private String transferRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "discrepancy_type", nullable = false)
    private DiscrepancyType discrepancyType;

    @Column(name = "internal_amount")
    private Long internalAmount;

    @Column(name = "bank_amount")
    private Long bankAmount;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Discrepancy missingInBank(LocalDate date, String externalId, String transferRef, long internalAmount) {
        Discrepancy d = new Discrepancy();
        d.reconciliationDate = date;
        d.externalId = externalId;
        d.transferRef = transferRef;
        d.discrepancyType = DiscrepancyType.MISSING_IN_BANK;
        d.internalAmount = internalAmount;
        d.createdAt = Instant.now();
        return d;
    }

    public static Discrepancy missingInInternal(LocalDate date, String transferRef, long bankAmount) {
        Discrepancy d = new Discrepancy();
        d.reconciliationDate = date;
        d.transferRef = transferRef;
        d.discrepancyType = DiscrepancyType.MISSING_IN_INTERNAL;
        d.bankAmount = bankAmount;
        d.createdAt = Instant.now();
        return d;
    }

    public static Discrepancy amountMismatch(LocalDate date, String externalId, String transferRef,
                                             long internalAmount, long bankAmount) {
        Discrepancy d = new Discrepancy();
        d.reconciliationDate = date;
        d.externalId = externalId;
        d.transferRef = transferRef;
        d.discrepancyType = DiscrepancyType.AMOUNT_MISMATCH;
        d.internalAmount = internalAmount;
        d.bankAmount = bankAmount;
        d.detail = "internal=" + internalAmount + ", bank=" + bankAmount;
        d.createdAt = Instant.now();
        return d;
    }

    public void resolve() {
        this.resolvedAt = Instant.now();
    }
}
