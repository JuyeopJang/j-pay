package juyeop.jpay.ledger.entity;

import jakarta.persistence.*;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.ledger.config.SnowflakeId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Table(name = "ledger_entries", indexes = {
		@Index(name = "idx_le_transaction", columnList = "transaction_id"),
		@Index(name = "idx_le_account_created", columnList = "account_id, created_at")
})
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerEntry {
	@Id
	@SnowflakeId
	private Long id;

	@Column(name = "transaction_id", nullable = false)
	private Long transactionId;

	@Column(name = "account_id", nullable = false)
	private Long accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NormalSide side;

	@Column(nullable = false)
	private Money amount;

	@CreatedDate
	@Column(name = "created_at")
	private Instant createdAt;
}
