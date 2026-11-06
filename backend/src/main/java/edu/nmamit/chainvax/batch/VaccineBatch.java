package edu.nmamit.chainvax.batch;

import java.time.Instant;
import java.time.LocalDate;

import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.policy.ExcursionPolicy;
import edu.nmamit.chainvax.site.Site;
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
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * A vaccine batch (lot of cartons) tracked as one custody unit. {@code @Version} gives optimistic
 * locking so concurrent stock issues cannot both decrement from the same snapshot, and Envers
 * keeps a revision history of every change.
 */
@Entity
@Table(name = "vaccine_batch")
@Audited
public class VaccineBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, unique = true, length = 40)
    private String batchCode;

    @Column(nullable = false, length = 80)
    private String product;

    @Column(name = "lot_number", nullable = false, length = 40)
    private String lotNumber;

    @Column(length = 80)
    private String manufacturer;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "doses_per_vial", nullable = false)
    private int dosesPerVial;

    @Column(name = "initial_vials", nullable = false)
    private int initialVials;

    @Column(name = "vials_on_hand", nullable = false)
    private int vialsOnHand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "current_site_id", nullable = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    private Site currentSite;

    @Column(name = "current_custodian", length = 60)
    private String currentCustodian;

    @ManyToOne(optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private ExcursionPolicy policy;

    @Version
    @Column(nullable = false)
    private long version;

    protected VaccineBatch() {
    }

    public VaccineBatch(String batchCode, String product, String lotNumber, String manufacturer, LocalDate expiryDate,
            int dosesPerVial, int vials, Site site, String custodian, ExcursionPolicy policy, Instant registeredAt) {
        this.batchCode = batchCode;
        this.product = product;
        this.lotNumber = lotNumber;
        this.manufacturer = manufacturer;
        this.expiryDate = expiryDate;
        this.dosesPerVial = dosesPerVial;
        this.initialVials = vials;
        this.vialsOnHand = vials;
        this.status = BatchStatus.VIABLE;
        this.statusReason = "Registered into custody";
        this.statusChangedAt = registeredAt;
        this.currentSite = site;
        this.currentCustodian = custodian;
        this.policy = policy;
    }

    public void changeStatus(BatchStatus next, String reason, Instant at) {
        this.status = next;
        this.statusReason = reason == null ? null : (reason.length() > 500 ? reason.substring(0, 497) + "..." : reason);
        this.statusChangedAt = at;
    }

    public void moveTo(Site site, String custodian) {
        this.currentSite = site;
        this.currentCustodian = custodian;
    }

    public void issue(int vials) {
        if (vials <= 0) {
            throw new IllegalArgumentException("Vials to issue must be positive.");
        }
        if (vials > vialsOnHand) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK",
                    batchCode + " has only " + vialsOnHand + " vials on hand (requested " + vials + ").");
        }
        vialsOnHand -= vials;
    }

    /** Issuable = VIABLE, not expired and not empty. CONDEMNED and AT_RISK stock is never issuable. */
    public boolean isIssuableOn(LocalDate day) {
        return status == BatchStatus.VIABLE && vialsOnHand > 0 && !expiryDate.isBefore(day);
    }

    /**
     * Admin console: correct registration details. Status, location, custodian and vials are NOT
     * editable here - they only change through the lifecycle, custody and stock services.
     */
    public void correctDetails(String product, String lotNumber, String manufacturer, LocalDate expiryDate,
            int dosesPerVial, ExcursionPolicy policy) {
        this.product = product;
        this.lotNumber = lotNumber;
        this.manufacturer = manufacturer;
        this.expiryDate = expiryDate;
        this.dosesPerVial = dosesPerVial;
        this.policy = policy;
    }

    public Long getId() {
        return id;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public String getProduct() {
        return product;
    }

    public String getLotNumber() {
        return lotNumber;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public int getDosesPerVial() {
        return dosesPerVial;
    }

    public int getInitialVials() {
        return initialVials;
    }

    public int getVialsOnHand() {
        return vialsOnHand;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Site getCurrentSite() {
        return currentSite;
    }

    public String getCurrentCustodian() {
        return currentCustodian;
    }

    public ExcursionPolicy getPolicy() {
        return policy;
    }

    public long getVersion() {
        return version;
    }
}
