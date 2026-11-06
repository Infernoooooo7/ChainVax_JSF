package edu.nmamit.chainvax.policy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import edu.nmamit.chainvax.audit.OverrideAudit;
import edu.nmamit.chainvax.audit.OverrideAuditRepository;
import edu.nmamit.chainvax.audit.OverrideType;
import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.common.CacheInvalidator;
import edu.nmamit.chainvax.common.NotFoundException;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
@Tag(name = "Excursion policies")
public class PolicyController {

    private final PolicyRepository policies;
    private final OverrideAuditRepository overrides;
    private final CurrentUserService currentUser;
    private final CacheInvalidator caches;
    private final Clock clock;

    public PolicyController(PolicyRepository policies, OverrideAuditRepository overrides,
            CurrentUserService currentUser, CacheInvalidator caches, Clock clock) {
        this.policies = policies;
        this.overrides = overrides;
        this.currentUser = currentUser;
        this.caches = caches;
        this.clock = clock;
    }

    public record PolicyView(Long id, String code, String name, String basis, boolean demoOnly, BigDecimal heatAlarmC,
            BigDecimal coldAlarmC, int atRiskAfterSeconds, int heatCondemnAfterSeconds, int coldCondemnAfterSeconds,
            long version, String updatedBy, Instant updatedAt) {

        public static PolicyView of(ExcursionPolicy p) {
            return new PolicyView(p.getId(), p.getCode(), p.getName(), p.getBasis(), p.isDemoOnly(), p.getHeatAlarmC(),
                    p.getColdAlarmC(), p.getAtRiskAfterSeconds(), p.getHeatCondemnAfterSeconds(),
                    p.getColdCondemnAfterSeconds(), p.getVersion(), p.getUpdatedBy(), p.getUpdatedAt());
        }
    }

    public record PolicyUpdateRequest(
            @NotNull @DecimalMin("-30.0") @DecimalMax("30.0") BigDecimal heatAlarmC,
            @NotNull @DecimalMin("-30.0") @DecimalMax("30.0") BigDecimal coldAlarmC,
            @Min(0) @Max(604800) int atRiskAfterSeconds,
            @Min(1) @Max(604800) int heatCondemnAfterSeconds,
            @Min(1) @Max(604800) int coldCondemnAfterSeconds,
            @NotBlank @Size(min = 10, max = 500) String reason,
            Long expectedVersion) {
    }

    @GetMapping
    @PreAuthorize(Access.STAFF)
    public List<PolicyView> list() {
        return policies.findAllByOrderByIdAsc().stream().map(PolicyView::of).toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize(Access.OFFICER)
    @Transactional
    @Operation(summary = "Change a policy's thresholds (district officer; recorded as an officer-attributed override)")
    public PolicyView update(@PathVariable long id, @Valid @RequestBody PolicyUpdateRequest request,
            Authentication authentication) {
        AppUser officer = currentUser.require(authentication);
        ExcursionPolicy policy = policies.findById(id).orElseThrow(() -> new NotFoundException("No policy " + id));
        if (request.expectedVersion() != null && request.expectedVersion() != policy.getVersion()) {
            throw new BusinessRuleException("CONCURRENT_UPDATE",
                    "Policy " + policy.getCode() + " was changed by someone else (version " + policy.getVersion()
                            + ", you edited version " + request.expectedVersion() + "). Reload and retry.");
        }
        if (request.coldAlarmC().compareTo(request.heatAlarmC()) >= 0) {
            throw new BusinessRuleException("INVALID_POLICY", "The cold alarm threshold must be below the heat alarm threshold.");
        }
        if (request.atRiskAfterSeconds() > Math.min(request.heatCondemnAfterSeconds(), request.coldCondemnAfterSeconds())) {
            throw new BusinessRuleException("INVALID_POLICY", "The at-risk delay must not exceed the condemn durations.");
        }
        String before = policy.describeThresholds();
        policy.update(request.heatAlarmC(), request.coldAlarmC(), request.atRiskAfterSeconds(),
                request.heatCondemnAfterSeconds(), request.coldCondemnAfterSeconds(), officer.getUsername(), clock.instant());
        overrides.save(new OverrideAudit(OverrideType.POLICY_CHANGE, null, null, officer, request.reason(),
                "Policy " + policy.getCode() + " changed. Before: " + before + ". After: " + policy.describeThresholds() + ".",
                clock.instant()));
        caches.dashboardChanged();
        // Flush first so the response carries the incremented @Version the client must send next time.
        return PolicyView.of(policies.saveAndFlush(policy));
    }
}
