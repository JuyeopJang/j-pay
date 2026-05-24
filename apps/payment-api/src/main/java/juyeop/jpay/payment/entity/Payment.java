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

@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payment_external_id", columnNames = "external_id"))
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {

	@Id
	@SnowflakeId
	private Long id;

	@Column(name = "external_id", nullable = false)
	private String externalId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "merchant_id", nullable = false)
	private String merchantId;

	@Column(nullable = false)
	private Money amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

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

	public static Payment pending(String externalId, Long userId, Money amount, String merchantId) {
		Payment payment = new Payment();
		payment.externalId = externalId;
		payment.userId = userId;
		payment.amount = amount;
		payment.merchantId = merchantId;
		payment.status = PaymentStatus.PENDING;
		payment.requestedAt = Instant.now();
		return payment;
	}

	public static Payment completed(String externalId, Long userId, Money amount, String merchantId) {
		Payment payment = new Payment();
		payment.externalId = externalId;
		payment.userId = userId;
		payment.amount = amount;
		payment.merchantId = merchantId;
		payment.status = PaymentStatus.COMPLETED;
		payment.requestedAt = Instant.now();
		payment.completedAt = Instant.now();
		return payment;
	}

	public void complete() {
		if (this.status != PaymentStatus.PENDING) {
			throw new IllegalStateException("Payment is not in PENDING status");
		}
		this.status = PaymentStatus.COMPLETED;
		this.completedAt = Instant.now();
	}

	public void fail(String reason) {
		if (this.status != PaymentStatus.PENDING) {
			throw new IllegalStateException("Payment is not in PENDING status");
		}
		this.status = PaymentStatus.FAILED;
		this.failureReason = reason;
		this.completedAt = Instant.now();
	}

	public boolean matches(Long userId, Money amount, String merchantId) {
		return this.userId.equals(userId) && this.amount.equals(amount) && this.merchantId.equals(merchantId);
	}
}