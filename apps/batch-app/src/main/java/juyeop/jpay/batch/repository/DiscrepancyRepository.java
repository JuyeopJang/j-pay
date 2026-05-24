package juyeop.jpay.batch.repository;

import juyeop.jpay.batch.entity.Discrepancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, Long> {

    List<Discrepancy> findByReconciliationDateAndResolvedAtIsNull(LocalDate date);
}
