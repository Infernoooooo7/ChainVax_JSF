package edu.nmamit.chainvax.batch;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRepository extends JpaRepository<VaccineBatch, Long> {

    Optional<VaccineBatch> findByBatchCode(String batchCode);

    List<VaccineBatch> findAllByOrderByIdAsc();

    List<VaccineBatch> findByCurrentSiteId(Long siteId);

    List<VaccineBatch> findByCurrentSiteIdOrderByExpiryDateAscIdAsc(Long siteId);

    /** Candidate stock for first-expiry-first-out allocation. */
    List<VaccineBatch> findByCurrentSiteIdAndProductOrderByExpiryDateAscIdAsc(Long siteId, String product);

    long countByStatus(BatchStatus status);
}
