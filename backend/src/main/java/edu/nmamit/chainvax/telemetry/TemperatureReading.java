package edu.nmamit.chainvax.telemetry;

import java.math.BigDecimal;
import java.time.Instant;

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
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "temperature_reading")
public class TemperatureReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private LoggerDevice device;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "temperature_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal temperatureC;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReadingSource source;

    @Column(nullable = false)
    private boolean late;

    protected TemperatureReading() {
    }

    public TemperatureReading(LoggerDevice device, Site site, Instant recordedAt, Instant receivedAt,
            BigDecimal temperatureC, ReadingSource source, boolean late) {
        this.device = device;
        this.site = site;
        this.recordedAt = recordedAt;
        this.receivedAt = receivedAt;
        this.temperatureC = temperatureC;
        this.source = source;
        this.late = late;
    }

    public Long getId() {
        return id;
    }

    public LoggerDevice getDevice() {
        return device;
    }

    public Site getSite() {
        return site;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public BigDecimal getTemperatureC() {
        return temperatureC;
    }

    public ReadingSource getSource() {
        return source;
    }

    public boolean isLate() {
        return late;
    }
}
