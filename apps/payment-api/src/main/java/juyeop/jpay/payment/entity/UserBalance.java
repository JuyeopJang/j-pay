package juyeop.jpay.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import juyeop.jpay.common.core.InsufficientFundsException;
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

@Table(name = "user_balance",
		uniqueConstraints = @UniqueConstraint(name = "uk_user_balance_user_id", columnNames = "user_id"))
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserBalance {

	@Id
	@SnowflakeId
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false)
	private Money balance;

	@Version
	private Long version;

	@CreatedDate
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public static UserBalance create(Long userId, Money balance) {
		UserBalance ub = new UserBalance();
		ub.userId = userId;
		ub.balance = balance;
		return ub;
	}

	public void deduct(Money amount) {
		balance = balance.minus(amount);
	}
}