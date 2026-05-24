package juyeop.jpay.ledger.repository;

import juyeop.jpay.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
}