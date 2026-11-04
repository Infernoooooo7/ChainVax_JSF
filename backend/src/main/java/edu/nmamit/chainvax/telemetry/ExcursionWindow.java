package edu.nmamit.chainvax.telemetry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.batch.VaccineBatch;
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

/**
 * One continuous out-of-range period experienced by a batch. It stays open until an in-range
 * reading arrives for the batch's location, so a logger that goes silent mid-excursion cannot
 * hide a sustained excursion from the scheduled re-evaluation worker.
 */
@Entity
@Table(name = "excursion_window")
public class ExcursionWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private VaccineBatch batch;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private LoggerDevice device;

    @Column(length = 60)
    private String custodian;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExcursionType type;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_reading_at", nullable = false)
    private Instant lastReadingAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "peak_temperature_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal peakTemperatureC;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BatchStatus outcome;

    protected ExcursionWindow() {
    }

    public ExcursionWindow(VaccineBatch batch, Site site, LoggerDevice device, String custodian, ExcursionType type,
            Instant startedAt, BigDecimal temperatureC) {
        this.batch = batch;
        this.site = site;
        this.device = device;
        this.custodian = custodian;
        this.type = type;
        this.startedAt = startedAt;
        this.lastReadingAt = startedAt;
        this.peakTemperatureC = temperatureC;
        this.open = true;
    }

    public void extend(Instant at, BigDecimal temperatureC) {
        if (at.isAfter(lastReadingAt)) {
            lastReadingAt = at;
        }
        boolean moreExtreme = type == ExcursionType.HEAT
                ? temperatureC.compareTo(peakTemperatureC) > 0
                : temperatureC.compareTo(peakTemperatureC) < 0;
        if (moreExtreme) {
            peakTemperatureC = temperatureC;
        }
    }

    public void close(Instant at) {
        this.open = false;
        this.endedAt = at;
    }

    public void recordOutcome(BatchStatus status) {
        if (outcome == null || status.rank() > outcome.rank()) {
            outcome = status;
        }
    }

    /** Exposure so far: until {@code now} while open, otherwise until the closing reading. */
    public Duration exposure(Instant now) {
        Instant end = open ? now : endedAt;
        Duration d = Duration.between(startedAt, end);
        return d.isNegative() ? Duration.ZERO : d;
    }

    public Long getId() {
        return id;
    }

    public VaccineBatch getBatch() {
        return batch;
    }

    public Site getSite() {
        return site;
    }

    public LoggerDevice getDevice() {
        return device;
    }

    public String getCustodian() {
        return custodian;
    }

    public ExcursionType getType() {
        return type;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastReadingAt() {
        return lastReadingAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public BigDecimal getPeakTemperatureC() {
        return peakTemperatureC;
    }

    public boolean isOpen() {
        return open;
    }

    public BatchStatus getOutcome() {
        return outcome;
    }
}
