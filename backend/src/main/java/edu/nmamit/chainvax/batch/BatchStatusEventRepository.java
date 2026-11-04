package edu.nmamit.chainvax.batch;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchStatusEventRepository extends JpaRepository<BatchStatusEvent, Long> {

    List<BatchStatusEvent> findByBatchIdOrderByIdAsc(Long batchId);

    List<BatchStatusEvent> findTop100ByOrderByIdDesc();

    List<BatchStatusEvent> findByOccurredAtAfterOrderByIdAsc(Instant after);
}
