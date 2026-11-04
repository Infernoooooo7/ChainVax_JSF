package edu.nmamit.chainvax.batch;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Lifecycle transition, including where the batch was and who held it at the time - so spoilage
 * can be traced to the custodian responsible.
 */
@Entity
@Immutable
@Table(name = "batch_status_event")
public class BatchStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private VaccineBatch batch;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private BatchStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private BatchStatus toStatus;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(name = "site_code", length = 40)
    private String siteCode;

    @Column(length = 60)
    private String custodian;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected BatchStatusEvent() {
    }

    public BatchStatusEvent(VaccineBatch batch, BatchStatus fromStatus, BatchStatus toStatus, String reason,
            String actor, String siteCode, String custodian, Instant occurredAt) {
        this.batch = batch;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason.length() > 500 ? reason.substring(0, 497) + "..." : reason;
        this.actor = actor;
        this.siteCode = siteCode;
        this.custodian = custodian;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public VaccineBatch getBatch() {
        return batch;
    }

    public BatchStatus getFromStatus() {
        return fromStatus;
    }

    public BatchStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getActor() {
        return actor;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public String getCustodian() {
        return custodian;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
