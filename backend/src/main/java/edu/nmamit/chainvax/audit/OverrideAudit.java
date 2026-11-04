package edu.nmamit.chainvax.audit;

import java.time.Instant;

import edu.nmamit.chainvax.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Append-only record of every override, attributed to the officer who made it. */
@Entity
@Immutable
@Table(name = "override_audit")
public class OverrideAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OverrideType type;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "batch_code", length = 40)
    private String batchCode;

    @Column(name = "officer_username", nullable = false, length = 60)
    private String officerUsername;

    @Column(name = "officer_name", nullable = false, length = 120)
    private String officerName;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false, length = 2000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OverrideAudit() {
    }

    public OverrideAudit(OverrideType type, Long batchId, String batchCode, AppUser officer, String reason,
            String details, Instant createdAt) {
        this.type = type;
        this.batchId = batchId;
        this.batchCode = batchCode;
        this.officerUsername = officer.getUsername();
        this.officerName = officer.getFullName();
        this.reason = reason;
        this.details = details;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public OverrideType getType() {
        return type;
    }

    public Long getBatchId() {
        return batchId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public String getOfficerUsername() {
        return officerUsername;
    }

    public String getOfficerName() {
        return officerName;
    }

    public String getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
