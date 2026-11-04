package edu.nmamit.chainvax.stock;

import java.time.Instant;

import edu.nmamit.chainvax.batch.VaccineBatch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Vials issued from a batch to a vaccination session. */
@Entity
@Immutable
@Table(name = "stock_issue")
public class StockIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private VaccineBatch batch;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Column(nullable = false)
    private int vials;

    @Column(name = "issued_by", nullable = false, length = 60)
    private String issuedBy;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "fefo_order", nullable = false)
    private boolean fefoOrder;

    @Column(name = "override_audit_id")
    private Long overrideAuditId;

    @Column(length = 160)
    private String purpose;

    protected StockIssue() {
    }

    public StockIssue(VaccineBatch batch, String siteCode, int vials, String issuedBy, Instant issuedAt,
            boolean fefoOrder, Long overrideAuditId, String purpose) {
        this.batch = batch;
        this.siteCode = siteCode;
        this.vials = vials;
        this.issuedBy = issuedBy;
        this.issuedAt = issuedAt;
        this.fefoOrder = fefoOrder;
        this.overrideAuditId = overrideAuditId;
        this.purpose = purpose;
    }

    public Long getId() {
        return id;
    }

    public VaccineBatch getBatch() {
        return batch;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public int getVials() {
        return vials;
    }

    public String getIssuedBy() {
        return issuedBy;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public boolean isFefoOrder() {
        return fefoOrder;
    }

    public Long getOverrideAuditId() {
        return overrideAuditId;
    }

    public String getPurpose() {
        return purpose;
    }
}
