package juyeop.jpay.ledger.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountType {
	USER_MONEY(NormalSide.CREDIT),
	MERCHANT_RECEIVABLE(NormalSide.CREDIT),
	PG_RECEIVABLE(NormalSide.DEBIT),
	OPERATING_CASH(NormalSide.DEBIT),
	FEE_REVENUE(NormalSide.CREDIT);

	private final NormalSide normalSide;
}
