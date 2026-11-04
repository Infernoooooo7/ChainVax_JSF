package edu.nmamit.chainvax.simulator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import edu.nmamit.chainvax.auth.TokenService.TokenResponse;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.common.NotFoundException;
import edu.nmamit.chainvax.config.ChainVaxProperties;
import edu.nmamit.chainvax.telemetry.IngestionService.IngestResult;
import edu.nmamit.chainvax.telemetry.LoggerDevice;
import edu.nmamit.chainvax.telemetry.LoggerDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Virtual data loggers. Each simulated logger authenticates with its own device secret and POSTs
 * to the same /api/telemetry/readings endpoint over HTTP that a real logger would use; readings
 * are tagged source=SIMULATOR.
 */
@Service
public class SimulatorService {

    public enum Mode {
        NORMAL, HEAT, COLD, SILENT
    }

    private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);

    private final LoggerDeviceRepository devices;
    private final ChainVaxProperties properties;
    private final Environment environment;
    private final Clock clock;
    private final Map<String, Mode> modes = new ConcurrentHashMap<>();
    private final Map<String, TokenResponse> tokens = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private volatile boolean autoEnabled;
    private volatile RestClient client;

    public SimulatorService(LoggerDeviceRepository devices, ChainVaxProperties properties, Environment environment,
            Clock clock) {
        this.devices = devices;
        this.properties = properties;
        this.environment = environment;
        this.clock = clock;
        this.autoEnabled = properties.simulator().autoStart();
    }

    public record DeviceState(String deviceCode, String description, String siteCode, String siteName, Mode mode,
            Instant lastReadingAt, BigDecimal lastTemperatureC) {
    }

    public record SimulatorState(boolean autoEnabled, long intervalSeconds, String note, List<DeviceState> devices) {
    }

    public SimulatorState state() {
        List<DeviceState> list = devices.findAllByOrderByIdAsc().stream()
                .map(d -> new DeviceState(d.getDeviceCode(), d.getDescription(), d.getSite().getCode(),
                        d.getSite().getName(), modeOf(d.getDeviceCode()), d.getLastReadingAt(), d.getLastTemperatureC()))
                .toList();
        return new SimulatorState(autoEnabled, properties.simulator().interval().getSeconds(),
                "SIMULATED readings: NORMAL 4-6 C, HEAT 10.5-12 C, COLD -2 to -1 C. Posted over HTTP to /api/telemetry/readings with a device JWT.",
                list);
    }

    public SimulatorState setAuto(boolean enabled) {
        this.autoEnabled = enabled;
        return state();
    }

    /** Auto mode: every interval, each non-silent logger posts one reading for its current mode. */
    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "${chainvax.simulator.interval}")
    public void tick() {
        if (!autoEnabled) {
            return;
        }
        for (LoggerDevice device : devices.findAllByOrderByIdAsc()) {
            Mode mode = modeOf(device.getDeviceCode());
            if (mode == Mode.SILENT) {
                continue;
            }
            try {
                post(device.getDeviceCode(), sample(mode));
            }
            catch (RestClientException ex) {
                log.warn("Simulator post for {} failed: {}", device.getDeviceCode(), ex.getMessage());
            }
        }
    }

    /** Switch a logger's scenario and immediately post one reading so the effect is visible at once. */
    public IngestResult setMode(String deviceCode, Mode mode) {
        requireDevice(deviceCode);
        modes.put(deviceCode, mode);
        return mode == Mode.SILENT ? null : postOrExplain(deviceCode, sample(mode));
    }

    public IngestResult sendReading(String deviceCode, BigDecimal temperatureC) {
        requireDevice(deviceCode);
        return postOrExplain(deviceCode, temperatureC.setScale(2, RoundingMode.HALF_UP));
    }

    private IngestResult postOrExplain(String deviceCode, BigDecimal temperatureC) {
        try {
            return post(deviceCode, temperatureC);
        }
        catch (RestClientResponseException ex) {
            throw new BusinessRuleException("SIMULATOR_POST_FAILED", "Ingestion API answered "
                    + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString());
        }
    }

    private IngestResult post(String deviceCode, BigDecimal temperatureC) {
        Map<String, Object> reading = new LinkedHashMap<>();
        reading.put("recordedAt", clock.instant().toString());
        reading.put("temperatureC", temperatureC);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceCode", deviceCode);
        body.put("source", "SIMULATOR");
        body.put("readings", List.of(reading));
        return client().post()
                .uri("/api/telemetry/readings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(deviceCode))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(IngestResult.class);
    }

    private String token(String deviceCode) {
        TokenResponse cached = tokens.get(deviceCode);
        if (cached != null && cached.expiresAt().isAfter(clock.instant().plusSeconds(60))) {
            return cached.accessToken();
        }
        Map<String, String> credentials = Map.of("deviceCode", deviceCode, "deviceSecret",
                properties.simulator().deviceSecret());
        TokenResponse fresh = client().post()
                .uri("/api/auth/device-token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(credentials)
                .retrieve()
                .body(TokenResponse.class);
        tokens.put(deviceCode, fresh);
        return fresh.accessToken();
    }

    private RestClient client() {
        if (client == null) {
            String base = properties.simulator().baseUrl();
            if (base == null || base.isBlank()) {
                base = "http://localhost:" + environment.getProperty("local.server.port",
                        environment.getProperty("server.port", "8080"));
            }
            client = RestClient.builder().baseUrl(base).build();
        }
        return client;
    }

    private Mode modeOf(String deviceCode) {
        return modes.getOrDefault(deviceCode, Mode.NORMAL);
    }

    private void requireDevice(String deviceCode) {
        if (devices.findByDeviceCode(deviceCode).isEmpty()) {
            throw new NotFoundException("Unknown logger " + deviceCode);
        }
    }

    private BigDecimal sample(Mode mode) {
        double value;
        switch (mode) {
            case HEAT:
                value = 10.5 + random.nextDouble() * 1.5;
                break;
            case COLD:
                value = -2.0 + random.nextDouble();
                break;
            default:
                value = 4.0 + random.nextDouble() * 2.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
