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

	@Column(name = "payment_method_id", nullable = false)
	private String paymentMethodId;

	@Column(nullable = false)
	private Money amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ChargeStatus status;

	@Column(name = "pg_approval_number")
	private String pgApprovalNumber;

	@Column(name = "pg_response_meta", columnDefinition = "JSON")
	private String pgResponseMeta;

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
			String paymentMethodId
	) {
		Charge charge = new Charge();
		charge.externalId = externalId;
		charge.userId = userId;
		charge.amount = amount;
		charge.paymentMethodId = paymentMethodId;
		charge.status = ChargeStatus.PENDING;
		charge.requestedAt = Instant.now();
		return charge;
	}

	public void complete(String pgApprovalNumber, String pgResponseMeta) {
		if (this.status != ChargeStatus.PENDING) {
			throw new IllegalStateException("Charge is not in PENDING status");
		}
		this.status = ChargeStatus.COMPLETED;
		this.pgApprovalNumber = pgApprovalNumber;
		this.pgResponseMeta = pgResponseMeta;
		this.completedAt = Instant.now();
	}

	public void fail(String failureReason, String pgResponseMeta) {
		if (this.status != ChargeStatus.PENDING) {
			throw new IllegalStateException("Charge is not in PENDING status");
		}
		this.status = ChargeStatus.FAILED;
		this.failureReason = failureReason;
		this.pgResponseMeta = pgResponseMeta;
		this.completedAt = Instant.now();
	}

	public boolean matches(Long userId, Money amount, String paymentMethodId) {
		return this.userId.equals(userId)
				&& this.amount.equals(amount)
				&& this.paymentMethodId.equals(paymentMethodId);
	}
}