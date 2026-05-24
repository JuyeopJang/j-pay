package juyeop.jpay.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import juyeop.jpay.common.core.Money;
import juyeop.jpay.common.jpa.SnowflakeId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Table(name = "charges",
		uniqueConstraints = @UniqueConstraint(name = "uk_charge_external_id", columnNames = "external_id"))
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Charge {

	@Id
	@SnowflakeId
	private Long id;

	@Column(name = "external_id", nullable = false)
	private String externalId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "bank_account_id", nullable = false)
	private String bankAccountId;

	@Column(nullable = false)
	private Money amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChargeStatus status;

	@Column(name = "transfer_ref")
	private String transferRef;

	@Column(name = "bank_response_meta", columnDefinition = "JSON")
	private String bankResponseMeta;

	@Column(name = "failure_reason")
	private String failureReason;

	@Column(name = "requested_at", nullable = false)
	private Instant requestedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	public static Charge pending(
			String externalId,
			Long userId,
			Money amount,
			String bankAccountId
	) {
		Charge charge = new Charge();
		charge.externalId = externalId;
		charge.userId = userId;
		charge.amount = amount;
		charge.bankAccountId = bankAccountId;
		charge.status = ChargeStatus.PENDING;
		charge.requestedAt = Instant.now();
		return charge;
	}

	public void complete(String transferRef, String bankResponseMeta) {
		if (this.status != ChargeStatus.PENDING) {
			throw new IllegalStateException("Charge is not in PENDING status");
		}
		this.status = ChargeStatus.COMPLETED;
		this.transferRef = transferRef;
		this.bankResponseMeta = bankResponseMeta;
		this.completedAt = Instant.now();
	}

	public void fail(String failureReason, String bankResponseMeta) {
		if (this.status != ChargeStatus.PENDING) {
			throw new IllegalStateException("Charge is not in PENDING status");
		}
		this.status = ChargeStatus.FAILED;
		this.failureReason = failureReason;
		this.bankResponseMeta = bankResponseMeta;
		this.completedAt = Instant.now();
	}

	public boolean matches(Long userId, Money amount, String bankAccountId) {
		return this.userId.equals(userId)
				&& this.amount.equals(amount)
				&& this.bankAccountId.equals(bankAccountId);
	}
}