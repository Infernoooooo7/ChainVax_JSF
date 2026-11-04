package edu.nmamit.chainvax.custody;

import java.time.Instant;

import edu.nmamit.chainvax.batch.BatchStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * One custody ledger entry. {@code hash = SHA-256(previousHash + payload)} where payload is the
 * canonical JSON of the entry's fields, so changing any field, deleting or reordering entries
 * breaks verification.
 */
@Entity
@Immutable
@Table(name = "custody_ledger")
public class LedgerEntry {

    @Id
    @Column(name = "seq")
    private Long seq;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "batch_code", nullable = false, length = 40)
    private String batchCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private CustodyEventType eventType;

    @Column(name = "from_site_code", length = 40)
    private String fromSiteCode;

    @Column(name = "to_site_code", nullable = false, length = 40)
    private String toSiteCode;

    @Column(name = "from_custodian", length = 60)
    private String fromCustodian;

    @Column(name = "to_custodian", nullable = false, length = 60)
    private String toCustodian;

    @Column(nullable = false)
    private int vials;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_status", nullable = false, length = 20)
    private BatchStatus batchStatus;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_by", nullable = false, length = 60)
    private String recordedBy;

    @Column(length = 240)
    private String note;

    @Column(nullable = false, length = 4000)
    private String payload;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    protected LedgerEntry() {
    }

    LedgerEntry(LedgerHasher.Fields fields, String payload, String previousHash, String hash) {
        this.seq = fields.seq();
        this.batchId = fields.batchId();
        this.batchCode = fields.batchCode();
        this.eventType = fields.eventType();
        this.fromSiteCode = fields.fromSiteCode();
        this.toSiteCode = fields.toSiteCode();
        this.fromCustodian = fields.fromCustodian();
        this.toCustodian = fields.toCustodian();
        this.vials = fields.vials();
        this.batchStatus = fields.batchStatus();
        this.occurredAt = fields.occurredAt();
        this.recordedBy = fields.recordedBy();
        this.note = fields.note();
        this.payload = payload;
        this.previousHash = previousHash;
        this.hash = hash;
    }

    /** The hashed fields exactly as currently stored (used by verification). */
    public LedgerHasher.Fields fields() {
        return new LedgerHasher.Fields(seq, batchId, batchCode, eventType, fromSiteCode, toSiteCode, fromCustodian,
                toCustodian, vials, batchStatus, occurredAt, recordedBy, note);
    }

    public Long getSeq() {
        return seq;
    }

    public Long getBatchId() {
        return batchId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public CustodyEventType getEventType() {
        return eventType;
    }

    public String getFromSiteCode() {
        return fromSiteCode;
    }

    public String getToSiteCode() {
        return toSiteCode;
    }

    public String getFromCustodian() {
        return fromCustodian;
    }

    public String getToCustodian() {
        return toCustodian;
    }

    public int getVials() {
        return vials;
    }

    public BatchStatus getBatchStatus() {
        return batchStatus;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public String getNote() {
        return note;
    }

    public String getPayload() {
        return payload;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }
}
