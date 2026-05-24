package juyeop.jpay.transfer.entity;

import jakarta.persistence.*;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.jpa.SnowflakeId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Table(name = "transfers",
        uniqueConstraints = @UniqueConstraint(name = "uq_transfers_external_id", columnNames = "external_id"))
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transfer {

    @Id
    @SnowflakeId
    private Long id;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "bank_account_id", nullable = false)
    private String bankAccountId;

    @Column(nullable = false)
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "transfer_ref")
    private String transferRef;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Transfer pending(String externalId, String merchantId, String bankAccountId, Money amount) {
        Transfer t = new Transfer();
        t.externalId = externalId;
        t.merchantId = merchantId;
        t.bankAccountId = bankAccountId;
        t.amount = amount;
        t.status = TransferStatus.PENDING;
        return t;
    }

    public void complete(String transferRef) {
        if (this.status != TransferStatus.PENDING) {
            throw new IllegalStateException("Transfer is not in PENDING status");
        }
        this.status = TransferStatus.COMPLETED;
        this.transferRef = transferRef;
    }

    public void fail(String reason) {
        if (this.status != TransferStatus.PENDING) {
            throw new IllegalStateException("Transfer is not in PENDING status");
        }
        this.status = TransferStatus.FAILED;
        this.failureReason = reason;
    }

    public boolean matches(String merchantId, Money amount, String bankAccountId) {
        return this.merchantId.equals(merchantId)
                && this.amount.equals(amount)
                && this.bankAccountId.equals(bankAccountId);
    }
}
