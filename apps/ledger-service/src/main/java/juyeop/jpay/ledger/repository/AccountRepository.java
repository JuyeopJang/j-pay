package juyeop.jpay.ledger.repository;

import juyeop.jpay.ledger.entity.Account;
import juyeop.jpay.ledger.entity.AccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountTypeAndOwnerId(AccountType accountType, Long ownerId);
}