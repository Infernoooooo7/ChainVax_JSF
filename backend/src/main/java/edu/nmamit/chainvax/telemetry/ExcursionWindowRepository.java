package edu.nmamit.chainvax.telemetry;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExcursionWindowRepository extends JpaRepository<ExcursionWindow, Long> {

    Optional<ExcursionWindow> findByBatchIdAndOpenTrue(Long batchId);

    List<ExcursionWindow> findByOpenTrue();

    List<ExcursionWindow> findByBatchIdOrderByStartedAtDesc(Long batchId);

    List<ExcursionWindow> findTop100ByOrderByIdDesc();

    long countByOpenTrue();
}
