package edu.nmamit.chainvax.batch;

import java.time.Instant;

public record StatusEventView(Long id, Long batchId, String batchCode, BatchStatus fromStatus, BatchStatus toStatus,
        String reason, String actor, String siteCode, String custodian, Instant occurredAt) {

    public static StatusEventView of(BatchStatusEvent e) {
        return new StatusEventView(e.getId(), e.getBatch().getId(), e.getBatch().getBatchCode(), e.getFromStatus(),
                e.getToStatus(), e.getReason(), e.getActor(), e.getSiteCode(), e.getCustodian(), e.getOccurredAt());
    }
}
