package edu.nmamit.chainvax.batch;

import java.io.Serializable;
import java.time.Instant;

/** A lifecycle transition produced by ingestion, the scheduled worker or an officer. */
public record StatusChange(Long batchId, String batchCode, BatchStatus from, BatchStatus to, String reason, Instant at)
        implements Serializable {
}
