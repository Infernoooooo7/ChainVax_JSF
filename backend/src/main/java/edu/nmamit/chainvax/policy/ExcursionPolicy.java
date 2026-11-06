package edu.nmamit.chainvax.policy;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.envers.Audited;

/**
 * Configurable excursion policy. A reading at or above {@code heatAlarmC} is a HEAT excursion,
 * at or below {@code coldAlarmC} a COLD excursion (mirroring the "+8 C or above" / "-0.5 C or
 * below" wording of WHO PQS E006/TR06.4). A batch becomes AT_RISK once a single continuous
 * excursion lasts {@code atRiskAfterSeconds}, and CONDEMNED once it lasts the type's
 * condemn duration.
 */
@Entity
@Table(name = "excursion_policy")
@Audited
public class ExcursionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 1000)
    private String basis;

    @Column(name = "demo_only", nullable = false)
    private boolean demoOnly;

    @Column(name = "heat_alarm_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal heatAlarmC;

    @Column(name = "cold_alarm_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal coldAlarmC;

    @Column(name = "at_risk_after_seconds", nullable = false)
    private int atRiskAfterSeconds;

    @Column(name = "heat_condemn_after_seconds", nullable = false)
    private int heatCondemnAfterSeconds;

    @Column(name = "cold_condemn_after_seconds", nullable = false)
    private int coldCondemnAfterSeconds;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ExcursionPolicy() {
    }

    public ExcursionPolicy(String code, String name, String basis, boolean demoOnly, BigDecimal heatAlarmC,
            BigDecimal coldAlarmC, int atRiskAfterSeconds, int heatCondemnAfterSeconds, int coldCondemnAfterSeconds) {
        this.code = code;
        this.name = name;
        this.basis = basis;
        this.demoOnly = demoOnly;
        this.heatAlarmC = heatAlarmC;
        this.coldAlarmC = coldAlarmC;
        this.atRiskAfterSeconds = atRiskAfterSeconds;
        this.heatCondemnAfterSeconds = heatCondemnAfterSeconds;
        this.coldCondemnAfterSeconds = coldCondemnAfterSeconds;
    }

    public void update(BigDecimal heatAlarmC, BigDecimal coldAlarmC, int atRiskAfterSeconds,
            int heatCondemnAfterSeconds, int coldCondemnAfterSeconds, String updatedBy, Instant updatedAt) {
        this.heatAlarmC = heatAlarmC;
        this.coldAlarmC = coldAlarmC;
        this.atRiskAfterSeconds = atRiskAfterSeconds;
        this.heatCondemnAfterSeconds = heatCondemnAfterSeconds;
        this.coldCondemnAfterSeconds = coldCondemnAfterSeconds;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public String describeThresholds() {
        return "heat >= " + heatAlarmC.toPlainString() + " C condemns after " + heatCondemnAfterSeconds
                + " s; cold <= " + coldAlarmC.toPlainString() + " C condemns after " + coldCondemnAfterSeconds
                + " s; at-risk after " + atRiskAfterSeconds + " s";
    }

    /** Admin console: edit the descriptive fields of a policy. */
    public void updateDescription(String name, String basis, boolean demoOnly) {
        this.name = name;
        this.basis = basis;
        this.demoOnly = demoOnly;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getBasis() {
        return basis;
    }

    public boolean isDemoOnly() {
        return demoOnly;
    }

    public BigDecimal getHeatAlarmC() {
        return heatAlarmC;
    }

    public BigDecimal getColdAlarmC() {
        return coldAlarmC;
    }

    public int getAtRiskAfterSeconds() {
        return atRiskAfterSeconds;
    }

    public int getHeatCondemnAfterSeconds() {
        return heatCondemnAfterSeconds;
    }

    public int getColdCondemnAfterSeconds() {
        return coldCondemnAfterSeconds;
    }

    public long getVersion() {
        return version;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
