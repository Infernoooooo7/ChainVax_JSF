package edu.nmamit.chainvax.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.nmamit.chainvax.audit.OverrideAuditRepository;
import edu.nmamit.chainvax.audit.OverrideView;
import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.batch.BatchStatusEventRepository;
import edu.nmamit.chainvax.batch.StatusEventView;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.common.CacheInvalidator;
import edu.nmamit.chainvax.common.NotFoundException;
import edu.nmamit.chainvax.custody.CustodyEventType;
import edu.nmamit.chainvax.custody.LedgerEntryRepository;
import edu.nmamit.chainvax.custody.LedgerEntryView;
import edu.nmamit.chainvax.custody.LedgerService;
import edu.nmamit.chainvax.custody.LedgerService.VerificationResult;
import edu.nmamit.chainvax.digest.AlertDigestRepository;
import edu.nmamit.chainvax.digest.DigestService.DigestView;
import edu.nmamit.chainvax.policy.ExcursionPolicy;
import edu.nmamit.chainvax.policy.PolicyRepository;
import edu.nmamit.chainvax.site.Site;
import edu.nmamit.chainvax.site.SiteRepository;
import edu.nmamit.chainvax.site.SiteType;
import edu.nmamit.chainvax.stock.StockController.StockIssueView;
import edu.nmamit.chainvax.stock.StockIssueRepository;
import edu.nmamit.chainvax.telemetry.ExcursionWindowRepository;
import edu.nmamit.chainvax.telemetry.LoggerDeviceRepository;
import edu.nmamit.chainvax.telemetry.ReadingSource;
import edu.nmamit.chainvax.telemetry.TemperatureReadingRepository;
import edu.nmamit.chainvax.telemetry.WindowView;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.AppUserRepository;
import edu.nmamit.chainvax.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin console: vaccine batches (create = registration into the hash-chained ledger; edit =
 * correction of registration details; never deleted), read-only operational records (ledger,
 * readings, lifecycle events, excursions, overrides, stock issues, digests) and summary stats.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize(Access.ADMIN)
@Tag(name = "Admin console")
public class AdminRecordsController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "id");

    private final AdminQuery query;
    private final BatchRepository batches;
    private final SiteRepository sites;
    private final PolicyRepository policies;
    private final AppUserRepository users;
    private final LoggerDeviceRepository devices;
    private final LedgerService ledger;
    private final LedgerEntryRepository ledgerEntries;
    private final TemperatureReadingRepository readings;
    private final BatchStatusEventRepository events;
    private final ExcursionWindowRepository windows;
    private final OverrideAuditRepository overrides;
    private final StockIssueRepository issues;
    private final AlertDigestRepository digests;
    private final CacheInvalidator caches;
    private final CacheManager cacheManager;
    private final JdbcTemplate jdbc;
    private final Environment environment;
    private final Clock clock;

    public AdminRecordsController(AdminQuery query, BatchRepository batches, SiteRepository sites,
            PolicyRepository policies, AppUserRepository users, LoggerDeviceRepository devices, LedgerService ledger,
            LedgerEntryRepository ledgerEntries, TemperatureReadingRepository readings, BatchStatusEventRepository events,
            ExcursionWindowRepository windows, OverrideAuditRepository overrides, StockIssueRepository issues,
            AlertDigestRepository digests, CacheInvalidator caches, CacheManager cacheManager, JdbcTemplate jdbc,
            Environment environment, Clock clock) {
        this.query = query;
        this.batches = batches;
        this.sites = sites;
        this.policies = policies;
        this.users = users;
        this.devices = devices;
        this.ledger = ledger;
        this.ledgerEntries = ledgerEntries;
        this.readings = readings;
        this.events = events;
        this.windows = windows;
        this.overrides = overrides;
        this.issues = issues;
        this.digests = digests;
        this.caches = caches;
        this.cacheManager = cacheManager;
        this.jdbc = jdbc;
        this.environment = environment;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ batches

    public record AdminBatchView(Long id, String batchCode, String product, String lotNumber, String manufacturer,
            LocalDate expiryDate, int dosesPerVial, int initialVials, int vialsOnHand, BatchStatus status,
            String statusReason, Long siteId, String siteCode, String custodian, Long policyId, String policyCode,
            boolean issuable, long version) {

        static AdminBatchView of(VaccineBatch b, LocalDate today) {
            return new AdminBatchView(b.getId(), b.getBatchCode(), b.getProduct(), b.getLotNumber(), b.getManufacturer(),
                    b.getExpiryDate(), b.getDosesPerVial(), b.getInitialVials(), b.getVialsOnHand(), b.getStatus(),
                    b.getStatusReason(), b.getCurrentSite().getId(), b.getCurrentSite().getCode(), b.getCurrentCustodian(),
                    b.getPolicy().getId(), b.getPolicy().getCode(), b.isIssuableOn(today), b.getVersion());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminBatchRequest(
            @Size(max = 40) @Pattern(regexp = "[A-Z0-9-]*", message = "use upper-case letters, digits or '-'") String batchCode,
            @NotBlank @Size(max = 80) String product,
            @NotBlank @Size(max = 40) String lotNumber,
            @Size(max = 80) String manufacturer,
            @NotNull LocalDate expiryDate,
            @Min(1) @Max(50) int dosesPerVial,
            Integer initialVials,
            Long siteId,
            Long custodianId,
            @NotNull Long policyId,
            Long version) {
    }

    @GetMapping("/batches")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listBatches(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        LocalDate today = LocalDate.now(clock);
        return query.list("batches", batches.findAllByOrderByIdAsc().stream().map(b -> AdminBatchView.of(b, today)).toList(),
                filter, range, sort);
    }

    @GetMapping("/batches/{id}")
    @Transactional(readOnly = true)
    public AdminBatchView getBatch(@PathVariable long id) {
        return AdminBatchView.of(batch(id), LocalDate.now(clock));
    }

    @PostMapping("/batches")
    @Transactional
    @Operation(summary = "Register a new batch at a district store - appends a REGISTERED entry to the custody ledger")
    public AdminBatchView createBatch(@Valid @RequestBody AdminBatchRequest request, Authentication authentication) {
        if (request.batchCode() == null || request.batchCode().isBlank()) {
            throw new IllegalArgumentException("Batch code is required.");
        }
        if (batches.existsByBatchCode(request.batchCode())) {
            throw new BusinessRuleException("CODE_TAKEN", "Batch " + request.batchCode() + " already exists.");
        }
        if (request.initialVials() == null || request.initialVials() < 1) {
            throw new IllegalArgumentException("Vials must be at least 1.");
        }
        if (request.siteId() == null) {
            throw new IllegalArgumentException("Choose the district store receiving the batch.");
        }
        Site store = sites.findById(request.siteId()).orElseThrow(() -> new NotFoundException("No site " + request.siteId()));
        if (store.getType() != SiteType.DISTRICT_STORE) {
            throw new BusinessRuleException("NOT_A_STORE", "New stock enters the cold chain at a district store, not "
                    + store.getCode() + ". Use a custody transfer to move it on.");
        }
        AppUser custodian = (request.custodianId() == null ? java.util.Optional.<AppUser>empty() : users.findById(request.custodianId()))
                .orElseThrow(() -> new IllegalArgumentException("Choose the receiving custodian."));
        boolean canHold = custodian.getRole() == Role.DISTRICT_OFFICER
                || (custodian.getRole() == Role.STOREKEEPER && custodian.isAssignedTo(store));
        if (!canHold) {
            throw new BusinessRuleException("INVALID_CUSTODIAN", custodian.getUsername()
                    + " cannot receive stock at " + store.getCode() + " (must be its storekeeper or a district officer).");
        }
        if (!request.expiryDate().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("The expiry date must be in the future.");
        }
        Instant now = clock.instant();
        VaccineBatch batch = batches.save(new VaccineBatch(request.batchCode(), request.product().trim(),
                request.lotNumber().trim(), request.manufacturer(), request.expiryDate(), request.dosesPerVial(),
                request.initialVials(), store, custodian.getUsername(), policy(request.policyId()), now));
        ledger.append(batch, CustodyEventType.REGISTERED, null, store.getCode(), null, custodian.getUsername(),
                authentication.getName(), "Registered in the admin console", now);
        caches.dashboardChanged();
        return AdminBatchView.of(batch, LocalDate.now(clock));
    }

    @PutMapping("/batches/{id}")
    @Transactional
    @Operation(summary = "Correct registration details (status, location, custodian and vials are not editable here)")
    public AdminBatchView updateBatch(@PathVariable long id, @Valid @RequestBody AdminBatchRequest request) {
        VaccineBatch batch = batch(id);
        if (request.version() != null && request.version() != batch.getVersion()) {
            throw new BusinessRuleException("CONCURRENT_UPDATE", batch.getBatchCode()
                    + " was changed meanwhile (version " + batch.getVersion() + ", you edited version " + request.version()
                    + "). Reload and retry.");
        }
        batch.correctDetails(request.product().trim(), request.lotNumber().trim(), request.manufacturer(),
                request.expiryDate(), request.dosesPerVial(), policy(request.policyId()));
        batches.flush();
        caches.batchChanged(batch.getId());
        return AdminBatchView.of(batch, LocalDate.now(clock));
    }

    @DeleteMapping("/batches/{id}")
    public void deleteBatch(@PathVariable long id) {
        throw new BusinessRuleException("LEDGER_PROTECTED", "Batches are part of the hash-chained custody ledger and "
                + "cannot be deleted. Condemned or expired stock is withdrawn through the lifecycle instead.");
    }

    // ------------------------------------------------------------------ read-only records

    public record AdminReadingView(Long id, String deviceCode, String siteCode, Instant recordedAt, Instant receivedAt,
            BigDecimal temperatureC, ReadingSource source, boolean late) {
    }

    @GetMapping("/ledger")
    @Transactional(readOnly = true)
    @Operation(summary = "Custody ledger entries (read-only; id = seq)")
    public ResponseEntity<List<Map<String, Object>>> listLedger(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        List<Map<String, Object>> rows = ledgerEntries.findAllByOrderBySeqAsc().stream()
                .map(e -> withId(query.row(LedgerEntryView.of(e)), e.getSeq()))
                .toList();
        return query.list("ledger", rows, filter, range, sort);
    }

    @GetMapping("/ledger/verify")
    @Operation(summary = "Recompute and verify the whole SHA-256 chain")
    public VerificationResult verifyLedger() {
        return ledger.verify();
    }

    @GetMapping("/readings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listReadings(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        List<AdminReadingView> rows = readings.findAll(NEWEST_FIRST).stream()
                .map(r -> new AdminReadingView(r.getId(), r.getDevice().getDeviceCode(), r.getSite().getCode(),
                        r.getRecordedAt(), r.getReceivedAt(), r.getTemperatureC(), r.getSource(), r.isLate()))
                .toList();
        return query.list("readings", rows, filter, range, sort);
    }

    @GetMapping("/events")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listEvents(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        return query.list("events", events.findAll(NEWEST_FIRST).stream().map(StatusEventView::of).toList(), filter, range,
                sort);
    }

    @GetMapping("/windows")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listWindows(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        Instant now = clock.instant();
        return query.list("windows", windows.findAll(NEWEST_FIRST).stream().map(w -> WindowView.of(w, now)).toList(),
                filter, range, sort);
    }

    @GetMapping("/overrides")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listOverrides(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        return query.list("overrides", overrides.findAll(NEWEST_FIRST).stream().map(OverrideView::of).toList(), filter,
                range, sort);
    }

    @GetMapping("/issues")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listIssues(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        List<StockIssueView> rows = issues.findAll(NEWEST_FIRST).stream()
                .map(i -> new StockIssueView(i.getId(), i.getBatch().getBatchCode(), i.getBatch().getProduct(),
                        i.getSiteCode(), i.getVials(), i.getIssuedBy(), i.getIssuedAt(), i.isFefoOrder(),
                        i.getOverrideAuditId(), i.getPurpose()))
                .toList();
        return query.list("issues", rows, filter, range, sort);
    }

    @GetMapping("/digests")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listDigests(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        return query.list("digests", digests.findAll(NEWEST_FIRST).stream().map(DigestView::of).toList(), filter, range,
                sort);
    }

    // ------------------------------------------------------------------ dashboard stats

    @GetMapping("/stats")
    @Transactional(readOnly = true)
    @Operation(summary = "Counts for the admin dashboard")
    public Map<String, Object> stats() {
        VerificationResult verification = ledger.verify();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("users", users.count());
        stats.put("usersByRole", users.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(u -> u.getRole().name(), Collectors.counting())));
        stats.put("sites", sites.count());
        stats.put("loggers", devices.count());
        stats.put("policies", policies.count());
        stats.put("batches", batches.count());
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BatchStatus status : BatchStatus.values()) {
            byStatus.put(status.name(), batches.countByStatus(status));
        }
        stats.put("batchesByStatus", byStatus);
        stats.put("vialsOnHand", batches.findAllByOrderByIdAsc().stream().mapToInt(VaccineBatch::getVialsOnHand).sum());
        stats.put("ledgerEntries", verification.entriesChecked());
        stats.put("ledgerValid", verification.valid());
        stats.put("ledgerHeadHash", verification.headHash());
        stats.put("readings", readings.count());
        stats.put("openExcursions", windows.countByOpenTrue());
        stats.put("overrides", overrides.count());
        stats.put("stockIssues", issues.count());
        stats.put("digests", digests.count());
        stats.put("database", jdbc.queryForObject("select version()", String.class));
        stats.put("cacheProvider", cacheManager.getClass().getSimpleName());
        stats.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        return stats;
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> withId(Map<String, Object> row, Object id) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("id", id);
        copy.putAll(row);
        return copy;
    }

    private VaccineBatch batch(long id) {
        return batches.findById(id).orElseThrow(() -> new NotFoundException("No batch " + id));
    }

    private ExcursionPolicy policy(long id) {
        return policies.findById(id).orElseThrow(() -> new NotFoundException("No policy " + id));
    }

}
