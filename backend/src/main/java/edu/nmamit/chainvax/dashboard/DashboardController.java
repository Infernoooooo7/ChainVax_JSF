package edu.nmamit.chainvax.dashboard;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.config.ChainVaxProperties;
import edu.nmamit.chainvax.dashboard.DashboardService.DashboardSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize(Access.STAFF)
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboard;
    private final CacheManager cacheManager;
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final ChainVaxProperties properties;
    private final Clock clock;

    public DashboardController(DashboardService dashboard, CacheManager cacheManager, JdbcTemplate jdbc,
            Environment environment, ChainVaxProperties properties, Clock clock) {
        this.dashboard = dashboard;
        this.cacheManager = cacheManager;
        this.jdbc = jdbc;
        this.environment = environment;
        this.properties = properties;
        this.clock = clock;
    }

    public record SystemInfo(String cacheProvider, List<String> caches, String database, boolean embeddedDatabase,
            List<String> activeProfiles, boolean simulatorAutoStart, boolean workersEnabled, boolean demoControls,
            Instant serverTime) {
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard aggregates (cached; 'generatedAt' shows when the cached value was computed)")
    public DashboardSummary summary() {
        return dashboard.summary();
    }

    @GetMapping("/system/info")
    @Operation(summary = "Which database and cache provider this instance is using")
    public SystemInfo info() {
        String database = jdbc.queryForObject("select version()", String.class);
        return new SystemInfo(cacheManager.getClass().getSimpleName(), List.copyOf(cacheManager.getCacheNames()),
                database, properties.embeddedDb().enabled(), Arrays.asList(environment.getActiveProfiles()),
                properties.simulator().autoStart(), properties.worker().enabled(), properties.demo().tamperEndpoints(),
                clock.instant());
    }
}
