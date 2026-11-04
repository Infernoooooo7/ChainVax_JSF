package edu.nmamit.chainvax.digest;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** An expiry/alert digest "dispatched" to the in-app inbox (email/SMS delivery is out of demo scope). */
@Entity
@Immutable
@Table(name = "alert_digest")
public class AlertDigest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "trigger_source", nullable = false, length = 20)
    private String triggerSource;

    @Column(nullable = false, length = 200)
    private String headline;

    @Column(nullable = false, length = 8000)
    private String body;

    @Column(name = "ledger_head_hash", length = 64)
    private String ledgerHeadHash;

    @Column(name = "ledger_entries", nullable = false)
    private long ledgerEntries;

    @Column(name = "open_excursions", nullable = false)
    private int openExcursions;

    @Column(name = "at_risk_batches", nullable = false)
    private int atRiskBatches;

    @Column(name = "condemned_batches", nullable = false)
    private int condemnedBatches;

    @Column(name = "expiring_soon", nullable = false)
    private int expiringSoon;

    protected AlertDigest() {
    }

    public AlertDigest(Instant generatedAt, String triggerSource, String headline, String body, String ledgerHeadHash,
            long ledgerEntries, int openExcursions, int atRiskBatches, int condemnedBatches, int expiringSoon) {
        this.generatedAt = generatedAt;
        this.triggerSource = triggerSource;
        this.headline = headline.length() > 200 ? headline.substring(0, 197) + "..." : headline;
        this.body = body.length() > 8000 ? body.substring(0, 7997) + "..." : body;
        this.ledgerHeadHash = ledgerHeadHash;
        this.ledgerEntries = ledgerEntries;
        this.openExcursions = openExcursions;
        this.atRiskBatches = atRiskBatches;
        this.condemnedBatches = condemnedBatches;
        this.expiringSoon = expiringSoon;
    }

    public Long getId() {
        return id;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public String getHeadline() {
        return headline;
    }

    public String getBody() {
        return body;
    }

    public String getLedgerHeadHash() {
        return ledgerHeadHash;
    }

    public long getLedgerEntries() {
        return ledgerEntries;
    }

    public int getOpenExcursions() {
        return openExcursions;
    }

    public int getAtRiskBatches() {
        return atRiskBatches;
    }

    public int getCondemnedBatches() {
        return condemnedBatches;
    }

    public int getExpiringSoon() {
        return expiringSoon;
    }
}
