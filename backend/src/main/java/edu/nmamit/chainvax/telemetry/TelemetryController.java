package edu.nmamit.chainvax.telemetry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.common.ForbiddenActionException;
import edu.nmamit.chainvax.common.NotFoundException;
import edu.nmamit.chainvax.telemetry.IngestionService.IngestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
@Tag(name = "Telemetry")
public class TelemetryController {

    private static final Duration STALE_AFTER = Duration.ofMinutes(2);

    private final IngestionService ingestion;
    private final LoggerDeviceRepository devices;
    private final TemperatureReadingRepository readings;
    private final ExcursionWindowRepository windows;
    private final Clock clock;

    public TelemetryController(IngestionService ingestion, LoggerDeviceRepository devices,
            TemperatureReadingRepository readings, ExcursionWindowRepository windows, Clock clock) {
        this.ingestion = ingestion;
        this.devices = devices;
        this.readings = readings;
        this.windows = windows;
        this.clock = clock;
    }

    public record ReadingRequest(@NotNull Instant recordedAt,
            @NotNull @DecimalMin("-90.0") @DecimalMax("60.0") BigDecimal temperatureC) {
    }

    /** Payload a real data logger (or the built-in simulator) posts. */
    public record IngestRequest(@Size(max = 40) String deviceCode, ReadingSource source,
            @NotEmpty @Size(max = 500) List<@Valid ReadingRequest> readings) {
    }

    public record DeviceView(String deviceCode, String description, Long siteId, String siteCode, String siteName,
            String siteType, Instant lastReadingAt, BigDecimal lastTemperatureC, String state) {
    }

    public record ReadingPoint(Instant recordedAt, BigDecimal temperatureC, ReadingSource source, boolean late) {
    }

    @PostMapping("/readings")
    @PreAuthorize(Access.LOGGER)
    @Operation(summary = "Ingest temperature readings (logger device token required)",
            description = "Used by real data loggers and by the built-in simulator. A device token may only post for its own device.")
    public IngestResult ingest(@Valid @RequestBody IngestRequest request, @AuthenticationPrincipal Jwt jwt) {
        String tokenDevice = jwt.getClaimAsString("device");
        String device = request.deviceCode() == null || request.deviceCode().isBlank() ? tokenDevice : request.deviceCode();
        if (tokenDevice == null || !tokenDevice.equals(device)) {
            throw new ForbiddenActionException("A logger token may only submit readings for its own device ("
                    + tokenDevice + ").");
        }
        List<IngestionService.ReadingInput> inputs = request.readings().stream()
                .map(r -> new IngestionService.ReadingInput(r.recordedAt(), r.temperatureC()))
                .toList();
        return ingestion.ingest(device, inputs, request.source() == null ? ReadingSource.LOGGER : request.source());
    }

    @GetMapping("/devices")
    @PreAuthorize(Access.STAFF)
    @Transactional(readOnly = true)
    public List<DeviceView> devices() {
        Instant now = clock.instant();
        return devices.findAllByOrderByIdAsc().stream().map(d -> new DeviceView(d.getDeviceCode(), d.getDescription(),
                d.getSite().getId(), d.getSite().getCode(), d.getSite().getName(), d.getSite().getType().name(),
                d.getLastReadingAt(), d.getLastTemperatureC(), state(d, now))).toList();
    }

    @GetMapping("/devices/{deviceCode}/readings")
    @PreAuthorize(Access.STAFF)
    @Transactional(readOnly = true)
    public List<ReadingPoint> readings(@PathVariable String deviceCode,
            @RequestParam(defaultValue = "30") int minutes) {
        LoggerDevice device = devices.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new NotFoundException("Unknown logger " + deviceCode));
        int window = Math.max(1, Math.min(minutes, 24 * 60));
        Instant since = clock.instant().minus(Duration.ofMinutes(window));
        return readings.findByDeviceIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(device.getId(), since).stream()
                .map(r -> new ReadingPoint(r.getRecordedAt(), r.getTemperatureC(), r.getSource(), r.isLate()))
                .toList();
    }

    @GetMapping("/excursions")
    @PreAuthorize(Access.STAFF)
    @Transactional(readOnly = true)
    public List<WindowView> excursions(@RequestParam(defaultValue = "false") boolean openOnly) {
        Instant now = clock.instant();
        List<ExcursionWindow> list = openOnly ? windows.findByOpenTrue() : windows.findTop100ByOrderByIdDesc();
        return list.stream().map(w -> WindowView.of(w, now)).toList();
    }

    /** Display-only state using the WHO +2..+8 C storage band; policies decide batch status. */
    private static String state(LoggerDevice d, Instant now) {
        if (d.getLastReadingAt() == null) {
            return "NO_DATA";
        }
        if (d.getLastReadingAt().isBefore(now.minus(STALE_AFTER))) {
            return "STALE";
        }
        BigDecimal t = d.getLastTemperatureC();
        if (t.compareTo(new BigDecimal("8.00")) >= 0) {
            return "HEAT";
        }
        if (t.compareTo(new BigDecimal("-0.50")) <= 0) {
            return "FREEZE";
        }
        if (t.compareTo(new BigDecimal("2.00")) < 0) {
            return "BELOW_RANGE";
        }
        return "IN_RANGE";
    }
}
