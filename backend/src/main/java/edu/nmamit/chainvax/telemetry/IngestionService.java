package edu.nmamit.chainvax.telemetry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import edu.nmamit.chainvax.batch.StatusChange;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.common.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores logger readings and feeds them to the excursion engine in timestamp order. */
@Service
public class IngestionService {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(2);

    private final LoggerDeviceRepository devices;
    private final TemperatureReadingRepository readings;
    private final ExcursionService excursions;
    private final Clock clock;

    public IngestionService(LoggerDeviceRepository devices, TemperatureReadingRepository readings,
            ExcursionService excursions, Clock clock) {
        this.devices = devices;
        this.readings = readings;
        this.excursions = excursions;
        this.clock = clock;
    }

    public record ReadingInput(Instant recordedAt, BigDecimal temperatureC) {
    }

    public record IngestResult(String deviceCode, String siteCode, int accepted, int late,
            List<StatusChange> statusChanges) {
    }

    @Transactional
    public IngestResult ingest(String deviceCode, List<ReadingInput> inputs, ReadingSource source) {
        LoggerDevice device = devices.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new NotFoundException("Unknown logger " + deviceCode));
        Instant now = clock.instant();
        List<ReadingInput> ordered = new ArrayList<>(inputs);
        ordered.sort(Comparator.comparing(ReadingInput::recordedAt));

        int accepted = 0;
        int late = 0;
        List<StatusChange> changes = new ArrayList<>();
        for (ReadingInput input : ordered) {
            if (input.recordedAt().isAfter(now.plus(MAX_CLOCK_SKEW))) {
                throw new BusinessRuleException("FUTURE_READING",
                        "Reading timestamp " + input.recordedAt() + " is in the future - check the logger clock.");
            }
            BigDecimal temperature = input.temperatureC().setScale(2, RoundingMode.HALF_UP);
            // Readings older than the newest one already processed are stored but not re-evaluated.
            boolean isLate = device.getLastReadingAt() != null && !input.recordedAt().isAfter(device.getLastReadingAt());
            TemperatureReading reading = readings.save(new TemperatureReading(device, device.getSite(),
                    input.recordedAt(), now, temperature, source, isLate));
            if (isLate) {
                late++;
                continue;
            }
            device.recordReading(input.recordedAt(), temperature);
            changes.addAll(excursions.applyReading(reading));
            accepted++;
        }
        return new IngestResult(device.getDeviceCode(), device.getSite().getCode(), accepted, late, changes);
    }
}
