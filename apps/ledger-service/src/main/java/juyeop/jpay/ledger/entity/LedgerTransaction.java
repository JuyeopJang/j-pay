package juyeop.jpay.ledger.entity;

import jakarta.persistence.*;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.ledger.config.SnowflakeId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Table(name = "ledger_transactions",
		uniqueConstraints = @UniqueConstraint(name = "uk_ledger_tx_external_id", columnNames = "external_id"))
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerTransaction {
	@Id
	@SnowflakeId
	private Long id;

	@Column(name = "external_id", nullable = false)
	private String externalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false)
	private TransactionType transactionType;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_status", nullable = false)
	private TransactionStatus transactionStatus;

	@Column(name = "total_amount", nullable = false)
	private Money totalAmount;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@CreatedDate
	@Column(name = "created_at")
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private Instant updatedAt;

	@Version
	private Long version;
}
