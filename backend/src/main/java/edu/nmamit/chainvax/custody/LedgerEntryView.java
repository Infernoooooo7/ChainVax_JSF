package edu.nmamit.chainvax.custody;

import java.time.Instant;

public record LedgerEntryView(long seq, Long batchId, String batchCode, CustodyEventType eventType,
        String fromSiteCode, String toSiteCode, String fromCustodian, String toCustodian, int vials,
        String batchStatus, Instant occurredAt, String recordedBy, String note, String payload, String previousHash,
        String hash) {

    public static LedgerEntryView of(LedgerEntry e) {
        return new LedgerEntryView(e.getSeq(), e.getBatchId(), e.getBatchCode(), e.getEventType(),
                e.getFromSiteCode(), e.getToSiteCode(), e.getFromCustodian(), e.getToCustodian(), e.getVials(),
                e.getBatchStatus().name(), e.getOccurredAt(), e.getRecordedBy(), e.getNote(), e.getPayload(),
                e.getPreviousHash(), e.getHash());
    }
}
