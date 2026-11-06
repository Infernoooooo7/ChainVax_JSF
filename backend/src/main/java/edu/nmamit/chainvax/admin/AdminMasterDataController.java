package edu.nmamit.chainvax.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import edu.nmamit.chainvax.audit.OverrideAudit;
import edu.nmamit.chainvax.audit.OverrideAuditRepository;
import edu.nmamit.chainvax.audit.OverrideType;
import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.common.CacheInvalidator;
import edu.nmamit.chainvax.common.NotFoundException;
import edu.nmamit.chainvax.policy.ExcursionPolicy;
import edu.nmamit.chainvax.policy.PolicyController.PolicyView;
import edu.nmamit.chainvax.policy.PolicyRepository;
import edu.nmamit.chainvax.site.Site;
import edu.nmamit.chainvax.site.SiteRepository;
import edu.nmamit.chainvax.site.SiteType;
import edu.nmamit.chainvax.telemetry.LoggerDevice;
import edu.nmamit.chainvax.telemetry.LoggerDeviceRepository;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.AppUserRepository;
import edu.nmamit.chainvax.user.CurrentUserService;
import edu.nmamit.chainvax.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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
 * Admin console CRUD for master data: user accounts, sites, logger devices and excursion policies.
 * Endpoints follow react-admin's ra-data-simple-rest contract (see {@link AdminQuery}).
 * Deleting a row that other data still references is refused with 409 IN_USE.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize(Access.ADMIN)
@Tag(name = "Admin console")
public class AdminMasterDataController {

    private final AdminQuery query;
    private final AppUserRepository users;
    private final SiteRepository sites;
    private final LoggerDeviceRepository devices;
    private final PolicyRepository policies;
    private final BatchRepository batches;
    private final OverrideAuditRepository overrides;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUser;
    private final CacheInvalidator caches;
    private final Clock clock;

    public AdminMasterDataController(AdminQuery query, AppUserRepository users, SiteRepository sites,
            LoggerDeviceRepository devices, PolicyRepository policies, BatchRepository batches,
            OverrideAuditRepository overrides, PasswordEncoder passwordEncoder, CurrentUserService currentUser,
            CacheInvalidator caches, Clock clock) {
        this.query = query;
        this.users = users;
        this.sites = sites;
        this.devices = devices;
        this.policies = policies;
        this.batches = batches;
        this.overrides = overrides;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
        this.caches = caches;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ users

    public record AdminUserView(Long id, String username, String fullName, Role role, Long siteId, String siteCode,
            boolean enabled) {

        static AdminUserView of(AppUser u) {
            return new AdminUserView(u.getId(), u.getUsername(), u.getFullName(), u.getRole(),
                    u.getSite() == null ? null : u.getSite().getId(), u.siteCode(), u.isEnabled());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminUserRequest(
            @Size(max = 60) @Pattern(regexp = "[a-z0-9._-]*", message = "use lower-case letters, digits, '.', '_' or '-'") String username,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role,
            Long siteId,
            Boolean enabled,
            @Size(max = 100) String password) {
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    @Operation(summary = "List user accounts (react-admin list contract)")
    public ResponseEntity<List<Map<String, Object>>> listUsers(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        return query.list("users", users.findAllByOrderByIdAsc().stream().map(AdminUserView::of).toList(), filter, range, sort);
    }

    @GetMapping("/users/{id}")
    @Transactional(readOnly = true)
    public AdminUserView getUser(@PathVariable long id) {
        return AdminUserView.of(user(id));
    }

    @PostMapping("/users")
    @Transactional
    @Operation(summary = "Create a user account (password stored as a BCrypt hash)")
    public AdminUserView createUser(@Valid @RequestBody AdminUserRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (users.findByUsername(request.username()).isPresent()) {
            throw new BusinessRuleException("USERNAME_TAKEN", "Username '" + request.username() + "' is already taken.");
        }
        String hash = passwordEncoder.encode(password(request.password(), true));
        AppUser user = new AppUser(request.username(), hash, request.fullName().trim(), request.role(),
                siteFor(request.role(), request.siteId()));
        if (Boolean.FALSE.equals(request.enabled())) {
            user.updateAccount(user.getFullName(), user.getRole(), user.getSite(), false);
        }
        return AdminUserView.of(users.save(user));
    }

    @PutMapping("/users/{id}")
    @Transactional
    @Operation(summary = "Update a user account; a non-empty password resets it")
    public AdminUserView updateUser(@PathVariable long id, @Valid @RequestBody AdminUserRequest request,
            Authentication authentication) {
        AppUser user = user(id);
        if (request.username() != null && !request.username().isBlank() && !request.username().equals(user.getUsername())) {
            throw new BusinessRuleException("USERNAME_IMMUTABLE",
                    "Usernames cannot be changed - they appear in custody and audit history. Create a new account instead.");
        }
        boolean enabled = request.enabled() == null ? user.isEnabled() : request.enabled();
        boolean keepsAdmin = request.role() == Role.ADMIN && enabled;
        if (user.getUsername().equals(authentication.getName()) && !keepsAdmin) {
            throw new BusinessRuleException("SELF_LOCKOUT", "You cannot remove your own admin access.");
        }
        if (user.getRole() == Role.ADMIN && user.isEnabled() && !keepsAdmin && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("LAST_ADMIN", "At least one active administrator must remain.");
        }
        user.updateAccount(request.fullName().trim(), request.role(), siteFor(request.role(), request.siteId()), enabled);
        String newPassword = password(request.password(), false);
        if (newPassword != null) {
            user.changePassword(passwordEncoder.encode(newPassword));
        }
        return AdminUserView.of(user);
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    @Operation(summary = "Delete a user account (refused for yourself, the last admin, or a current custodian)")
    public AdminUserView deleteUser(@PathVariable long id, Authentication authentication) {
        AppUser user = user(id);
        AdminUserView view = AdminUserView.of(user);
        if (user.getUsername().equals(authentication.getName())) {
            throw new BusinessRuleException("SELF_DELETE", "You cannot delete your own account.");
        }
        if (user.getRole() == Role.ADMIN && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("LAST_ADMIN", "At least one active administrator must remain.");
        }
        if (batches.existsByCurrentCustodian(user.getUsername())) {
            throw new BusinessRuleException("HOLDS_STOCK", user.getUsername()
                    + " is the custodian of stock. Transfer it first, or deactivate the account instead.");
        }
        users.delete(user);
        users.flush();
        return view;
    }

    private Site siteFor(Role role, Long siteId) {
        if (role == Role.STOREKEEPER || role == Role.TRANSPORTER) {
            if (siteId == null) {
                throw new IllegalArgumentException("Storekeepers and transporters must be assigned to a site.");
            }
            return site(siteId);
        }
        return null; // district officers, auditors and admins are district-wide
    }

    private static String password(String password, boolean required) {
        if (password == null || password.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("A password of at least 8 characters is required.");
            }
            return null;
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("Passwords need at least 8 characters.");
        }
        return password;
    }

    // ------------------------------------------------------------------ sites

    public record AdminSiteView(Long id, String code, String name, SiteType type, String district, long batches,
            long staff, long loggers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminSiteRequest(
            @Size(max = 40) @Pattern(regexp = "[A-Z0-9-]*", message = "use upper-case letters, digits or '-'") String code,
            @NotBlank @Size(max = 120) String name,
            @NotNull SiteType type,
            @NotBlank @Size(max = 80) String district) {
    }

    @GetMapping("/sites")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listSites(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        Map<Long, Long> batchCount = countBy(batches.findAllByOrderByIdAsc(), b -> b.getCurrentSite().getId());
        Map<Long, Long> staffCount = countBy(users.findAllByOrderByIdAsc().stream().filter(u -> u.getSite() != null).toList(),
                u -> u.getSite().getId());
        Map<Long, Long> loggerCount = countBy(devices.findAllByOrderByIdAsc(), d -> d.getSite().getId());
        List<AdminSiteView> rows = sites.findAllByOrderByIdAsc().stream()
                .map(s -> new AdminSiteView(s.getId(), s.getCode(), s.getName(), s.getType(), s.getDistrict(),
                        batchCount.getOrDefault(s.getId(), 0L), staffCount.getOrDefault(s.getId(), 0L),
                        loggerCount.getOrDefault(s.getId(), 0L)))
                .toList();
        return query.list("sites", rows, filter, range, sort);
    }

    @GetMapping("/sites/{id}")
    @Transactional(readOnly = true)
    public AdminSiteView getSite(@PathVariable long id) {
        Site s = site(id);
        return new AdminSiteView(s.getId(), s.getCode(), s.getName(), s.getType(), s.getDistrict(),
                batches.countByCurrentSiteId(id), users.countBySiteId(id), devices.countBySiteId(id));
    }

    @PostMapping("/sites")
    @Transactional
    public AdminSiteView createSite(@Valid @RequestBody AdminSiteRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Site code is required.");
        }
        if (sites.findByCode(request.code()).isPresent()) {
            throw new BusinessRuleException("CODE_TAKEN", "Site code " + request.code() + " already exists.");
        }
        Site s = sites.save(new Site(request.code(), request.name().trim(), request.type(), request.district().trim()));
        caches.dashboardChanged();
        return new AdminSiteView(s.getId(), s.getCode(), s.getName(), s.getType(), s.getDistrict(), 0, 0, 0);
    }

    @PutMapping("/sites/{id}")
    @Transactional
    public AdminSiteView updateSite(@PathVariable long id, @Valid @RequestBody AdminSiteRequest request) {
        Site s = site(id);
        if (request.code() != null && !request.code().isBlank() && !request.code().equals(s.getCode())) {
            throw new BusinessRuleException("CODE_IMMUTABLE",
                    "Site codes are part of signed ledger history and cannot be changed.");
        }
        s.update(request.name().trim(), request.type(), request.district().trim());
        caches.dashboardChanged();
        return getSite(id);
    }

    @DeleteMapping("/sites/{id}")
    @Transactional
    public AdminSiteView deleteSite(@PathVariable long id) {
        AdminSiteView view = getSite(id);
        if (view.batches() + view.staff() + view.loggers() > 0) {
            throw new BusinessRuleException("IN_USE", "Site " + view.code() + " still has " + view.batches() + " batch(es), "
                    + view.staff() + " staff account(s) and " + view.loggers() + " logger(s). Move or remove them first.");
        }
        sites.delete(site(id));
        sites.flush(); // other references (e.g. stored readings) -> 409 IN_USE from the database FK
        caches.dashboardChanged();
        return view;
    }

    // ------------------------------------------------------------------ logger devices

    public record AdminDeviceView(Long id, String deviceCode, Long siteId, String siteCode, String description,
            Instant lastReadingAt, BigDecimal lastTemperatureC) {

        static AdminDeviceView of(LoggerDevice d) {
            return new AdminDeviceView(d.getId(), d.getDeviceCode(), d.getSite().getId(), d.getSite().getCode(),
                    d.getDescription(), d.getLastReadingAt(), d.getLastTemperatureC());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminDeviceRequest(
            @Size(max = 40) @Pattern(regexp = "[A-Z0-9-]*", message = "use upper-case letters, digits or '-'") String deviceCode,
            @NotNull Long siteId,
            @Size(max = 160) String description,
            @Size(max = 100) String secret) {
    }

    @GetMapping("/devices")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listDevices(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        return query.list("devices", devices.findAllByOrderByIdAsc().stream().map(AdminDeviceView::of).toList(), filter,
                range, sort);
    }

    @GetMapping("/devices/{id}")
    @Transactional(readOnly = true)
    public AdminDeviceView getDevice(@PathVariable long id) {
        return AdminDeviceView.of(device(id));
    }

    @PostMapping("/devices")
    @Transactional
    @Operation(summary = "Register a logger device (secret stored as a BCrypt hash)")
    public AdminDeviceView createDevice(@Valid @RequestBody AdminDeviceRequest request) {
        if (request.deviceCode() == null || request.deviceCode().isBlank()) {
            throw new IllegalArgumentException("Device code is required.");
        }
        if (devices.findByDeviceCode(request.deviceCode()).isPresent()) {
            throw new BusinessRuleException("CODE_TAKEN", "Logger " + request.deviceCode() + " already exists.");
        }
        String secret = password(request.secret(), true);
        LoggerDevice d = devices.save(new LoggerDevice(request.deviceCode(), site(request.siteId()),
                passwordEncoder.encode(secret), request.description()));
        return AdminDeviceView.of(d);
    }

    @PutMapping("/devices/{id}")
    @Transactional
    @Operation(summary = "Move a logger, change its description, or rotate its secret")
    public AdminDeviceView updateDevice(@PathVariable long id, @Valid @RequestBody AdminDeviceRequest request) {
        LoggerDevice d = device(id);
        if (request.deviceCode() != null && !request.deviceCode().isBlank() && !request.deviceCode().equals(d.getDeviceCode())) {
            throw new BusinessRuleException("CODE_IMMUTABLE", "Logger codes cannot be changed - readings reference them.");
        }
        d.update(site(request.siteId()), request.description());
        String secret = password(request.secret(), false);
        if (secret != null) {
            d.rotateSecret(passwordEncoder.encode(secret));
        }
        caches.dashboardChanged();
        return AdminDeviceView.of(d);
    }

    @DeleteMapping("/devices/{id}")
    @Transactional
    public AdminDeviceView deleteDevice(@PathVariable long id) {
        LoggerDevice d = device(id);
        AdminDeviceView view = AdminDeviceView.of(d);
        devices.delete(d);
        devices.flush(); // a logger with stored readings -> 409 IN_USE
        return view;
    }

    // ------------------------------------------------------------------ excursion policies

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdminPolicyRequest(
            @Size(max = 40) @Pattern(regexp = "[A-Z0-9-]*", message = "use upper-case letters, digits or '-'") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1000) String basis,
            Boolean demoOnly,
            @NotNull @DecimalMin("-30.0") @DecimalMax("30.0") BigDecimal heatAlarmC,
            @NotNull @DecimalMin("-30.0") @DecimalMax("30.0") BigDecimal coldAlarmC,
            @Min(0) @Max(604800) int atRiskAfterSeconds,
            @Min(1) @Max(604800) int heatCondemnAfterSeconds,
            @Min(1) @Max(604800) int coldCondemnAfterSeconds,
            Long version,
            @Size(max = 500) String reason) {
    }

    @GetMapping("/policies")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> listPolicies(@RequestParam(required = false) String filter,
            @RequestParam(required = false) String range, @RequestParam(required = false) String sort) {
        return query.list("policies", policies.findAllByOrderByIdAsc().stream().map(PolicyView::of).toList(), filter,
                range, sort);
    }

    @GetMapping("/policies/{id}")
    @Transactional(readOnly = true)
    public PolicyView getPolicy(@PathVariable long id) {
        return PolicyView.of(policy(id));
    }

    @PostMapping("/policies")
    @Transactional
    public PolicyView createPolicy(@Valid @RequestBody AdminPolicyRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("Policy code is required.");
        }
        if (policies.findByCode(request.code()).isPresent()) {
            throw new BusinessRuleException("CODE_TAKEN", "Policy " + request.code() + " already exists.");
        }
        checkThresholds(request);
        ExcursionPolicy p = policies.save(new ExcursionPolicy(request.code(), request.name().trim(), request.basis().trim(),
                Boolean.TRUE.equals(request.demoOnly()), celsius(request.heatAlarmC()), celsius(request.coldAlarmC()),
                request.atRiskAfterSeconds(), request.heatCondemnAfterSeconds(), request.coldCondemnAfterSeconds()));
        return PolicyView.of(p);
    }

    @PutMapping("/policies/{id}")
    @Transactional
    @Operation(summary = "Change a policy (version-checked; recorded as a POLICY_CHANGE audit record)")
    public PolicyView updatePolicy(@PathVariable long id, @Valid @RequestBody AdminPolicyRequest request,
            Authentication authentication) {
        ExcursionPolicy p = policy(id);
        if (request.version() != null && request.version() != p.getVersion()) {
            throw new BusinessRuleException("CONCURRENT_UPDATE", "Policy " + p.getCode()
                    + " was changed by someone else (version " + p.getVersion() + ", you edited version "
                    + request.version() + "). Reload and retry.");
        }
        if (request.reason() == null || request.reason().trim().length() < 10) {
            throw new IllegalArgumentException("A reason of at least 10 characters is required - it is recorded in the audit trail.");
        }
        checkThresholds(request);
        String before = p.describeThresholds();
        p.updateDescription(request.name().trim(), request.basis().trim(), Boolean.TRUE.equals(request.demoOnly()));
        p.update(celsius(request.heatAlarmC()), celsius(request.coldAlarmC()), request.atRiskAfterSeconds(), request.heatCondemnAfterSeconds(),
                request.coldCondemnAfterSeconds(), authentication.getName(), clock.instant());
        overrides.save(new OverrideAudit(OverrideType.POLICY_CHANGE, null, null, currentUser.require(authentication),
                request.reason().trim(), "Policy " + p.getCode() + " changed in the admin console. Before: " + before
                        + ". After: " + p.describeThresholds() + ".", clock.instant()));
        caches.dashboardChanged();
        return PolicyView.of(policies.saveAndFlush(p));
    }

    @DeleteMapping("/policies/{id}")
    @Transactional
    public PolicyView deletePolicy(@PathVariable long id) {
        ExcursionPolicy p = policy(id);
        PolicyView view = PolicyView.of(p);
        long assigned = batches.countByPolicyId(id);
        if (assigned > 0) {
            throw new BusinessRuleException("IN_USE", "Policy " + p.getCode() + " is assigned to " + assigned
                    + " batch(es). Reassign them first.");
        }
        policies.delete(p);
        policies.flush(); // a policy still assigned to batches -> 409 IN_USE
        return view;
    }

    /** Same scale as the numeric(5,2) columns, so audit text compares like with like (8.00, not 8). */
    private static BigDecimal celsius(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static void checkThresholds(AdminPolicyRequest request) {
        if (request.coldAlarmC().compareTo(request.heatAlarmC()) >= 0) {
            throw new IllegalArgumentException("The cold alarm must be below the heat alarm.");
        }
    }

    // ------------------------------------------------------------------ lookups

    private static <T> Map<Long, Long> countBy(List<T> items, Function<T, Long> key) {
        return items.stream().collect(Collectors.groupingBy(key, Collectors.counting()));
    }

    private AppUser user(long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("No user " + id));
    }

    private Site site(long id) {
        return sites.findById(id).orElseThrow(() -> new NotFoundException("No site " + id));
    }

    private LoggerDevice device(long id) {
        return devices.findById(id).orElseThrow(() -> new NotFoundException("No logger " + id));
    }

    private ExcursionPolicy policy(long id) {
        return policies.findById(id).orElseThrow(() -> new NotFoundException("No policy " + id));
    }
}
