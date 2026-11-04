package edu.nmamit.chainvax.custody;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findAllByOrderBySeqAsc();

    List<LedgerEntry> findByBatchIdOrderBySeqAsc(Long batchId);
}
