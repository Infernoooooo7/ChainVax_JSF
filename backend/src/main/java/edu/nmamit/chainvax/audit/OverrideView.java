package edu.nmamit.chainvax.audit;

import java.time.Instant;

public record OverrideView(Long id, OverrideType type, Long batchId, String batchCode, String officerUsername,
        String officerName, String reason, String details, Instant createdAt) {

    public static OverrideView of(OverrideAudit a) {
        return new OverrideView(a.getId(), a.getType(), a.getBatchId(), a.getBatchCode(), a.getOfficerUsername(),
                a.getOfficerName(), a.getReason(), a.getDetails(), a.getCreatedAt());
    }
}
