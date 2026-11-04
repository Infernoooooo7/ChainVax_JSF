package edu.nmamit.chainvax.telemetry;

import java.math.BigDecimal;
import java.time.Instant;

import edu.nmamit.chainvax.site.Site;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A temperature data logger installed at a site (store fridge, van cold box, PHC fridge). */
@Entity
@Table(name = "logger_device")
public class LoggerDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_code", nullable = false, unique = true, length = 40)
    private String deviceCode;

    @ManyToOne(optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "secret_hash", nullable = false, length = 100)
    private String secretHash;

    @Column(length = 160)
    private String description;

    @Column(name = "last_reading_at")
    private Instant lastReadingAt;

    @Column(name = "last_temperature_c", precision = 5, scale = 2)
    private BigDecimal lastTemperatureC;

    protected LoggerDevice() {
    }

    public LoggerDevice(String deviceCode, Site site, String secretHash, String description) {
        this.deviceCode = deviceCode;
        this.site = site;
        this.secretHash = secretHash;
        this.description = description;
    }

    public void recordReading(Instant at, BigDecimal temperatureC) {
        if (lastReadingAt == null || at.isAfter(lastReadingAt)) {
            lastReadingAt = at;
            lastTemperatureC = temperatureC;
        }
    }

    public Long getId() {
        return id;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public Site getSite() {
        return site;
    }

    public String getSecretHash() {
        return secretHash;
    }

    public String getDescription() {
        return description;
    }

    public Instant getLastReadingAt() {
        return lastReadingAt;
    }

    public BigDecimal getLastTemperatureC() {
        return lastTemperatureC;
    }
}
