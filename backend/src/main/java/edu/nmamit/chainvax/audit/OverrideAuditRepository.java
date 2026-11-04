package edu.nmamit.chainvax.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideAuditRepository extends JpaRepository<OverrideAudit, Long> {

    List<OverrideAudit> findTop100ByOrderByIdDesc();

    List<OverrideAudit> findByBatchIdOrderByIdDesc(Long batchId);
}
