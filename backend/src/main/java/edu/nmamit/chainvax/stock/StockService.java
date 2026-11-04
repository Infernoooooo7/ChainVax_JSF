package edu.nmamit.chainvax.stock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import edu.nmamit.chainvax.audit.OverrideAudit;
import edu.nmamit.chainvax.audit.OverrideAuditRepository;
import edu.nmamit.chainvax.audit.OverrideType;
import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.batch.BatchView;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.common.CacheInvalidator;
import edu.nmamit.chainvax.common.ForbiddenActionException;
import edu.nmamit.chainvax.common.NotFoundException;
import edu.nmamit.chainvax.site.Site;
import edu.nmamit.chainvax.site.SiteRepository;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * First-expiry-first-out (FEFO) stock allocator. Runs in one transaction; every touched batch is
 * protected by its {@code @Version}, so two concurrent issues against the same snapshot cannot both
 * succeed (the loser gets HTTP 409 and nothing is written).
 */
@Service
public class StockService {

    private final BatchRepository batches;
    private final SiteRepository sites;
    private final StockIssueRepository issues;
    private final OverrideAuditRepository overrides;
    private final CacheInvalidator caches;
    private final Clock clock;

    public StockService(BatchRepository batches, SiteRepository sites, StockIssueRepository issues,
            OverrideAuditRepository overrides, CacheInvalidator caches, Clock clock) {
        this.batches = batches;
        this.sites = sites;
        this.issues = issues;
        this.overrides = overrides;
        this.caches = caches;
        this.clock = clock;
    }

    public record IssueCommand(Long siteId, String product, int vials, String purpose, Long overrideBatchId,
            String overrideReason) {
    }

    public record Allocation(Long batchId, String batchCode, String lotNumber, LocalDate expiryDate, int vials,
            int remainingVials) {
    }

    public record WithdrawnBatch(Long batchId, String batchCode, String product, LocalDate expiryDate,
            BatchStatus status, int vialsOnHand, String reason) {
    }

    public record IssueResult(String siteCode, String product, int vialsIssued, boolean fefoOrder,
            List<Allocation> allocations, List<WithdrawnBatch> skipped, Long overrideAuditId) {
    }

    public record IssuableView(Long siteId, String siteCode, String siteName, String product, int issuableVials,
            List<BatchView> issuable, List<WithdrawnBatch> withdrawn) {
    }

    @Transactional(readOnly = true)
    public IssuableView issuable(Long siteId, String product) {
        Site site = site(siteId);
        LocalDate today = LocalDate.now(clock);
        List<VaccineBatch> stock = product == null || product.isBlank()
                ? batches.findByCurrentSiteIdOrderByExpiryDateAscIdAsc(siteId)
                : batches.findByCurrentSiteIdAndProductOrderByExpiryDateAscIdAsc(siteId, product);
        List<BatchView> issuable = new ArrayList<>();
        List<WithdrawnBatch> withdrawn = new ArrayList<>();
        int vials = 0;
        for (VaccineBatch b : stock) {
            if (b.isIssuableOn(today)) {
                issuable.add(BatchView.of(b, null, today));
                vials += b.getVialsOnHand();
            }
            else {
                withdrawn.add(withdrawn(b, today));
            }
        }
        return new IssuableView(site.getId(), site.getCode(), site.getName(), product, vials, issuable, withdrawn);
    }

    @Transactional
    public IssueResult issue(IssueCommand command, AppUser actor) {
        Site site = site(command.siteId());
        if (actor.getRole() != Role.STOREKEEPER && actor.getRole() != Role.DISTRICT_OFFICER) {
            throw new ForbiddenActionException("Only storekeepers and district officers can issue stock.");
        }
        if (actor.getRole() == Role.STOREKEEPER && !actor.isAssignedTo(site)) {
            throw new ForbiddenActionException("Storekeepers can only issue stock held at their own site ("
                    + actor.siteCode() + "), not " + site.getCode() + ".");
        }
        if (command.vials() <= 0) {
            throw new IllegalArgumentException("Vials to issue must be positive.");
        }
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        List<VaccineBatch> candidates = batches.findByCurrentSiteIdAndProductOrderByExpiryDateAscIdAsc(site.getId(),
                command.product());
        List<VaccineBatch> fefo = candidates.stream().filter(b -> b.isIssuableOn(today)).toList();
        List<WithdrawnBatch> skipped = candidates.stream().filter(b -> !b.isIssuableOn(today))
                .map(b -> withdrawn(b, today)).toList();

        List<Allocation> allocations = new ArrayList<>();
        Long auditId = null;
        if (command.overrideBatchId() != null) {
            auditId = issueWithOverride(command, actor, site, candidates, fefo, now, today, allocations);
        }
        else {
            int available = fefo.stream().mapToInt(VaccineBatch::getVialsOnHand).sum();
            if (available < command.vials()) {
                throw new BusinessRuleException("INSUFFICIENT_ISSUABLE_STOCK", "Only " + available + " issuable vials of "
                        + command.product() + " at " + site.getCode() + " (requested " + command.vials() + ")."
                        + (skipped.isEmpty() ? "" : " Withdrawn and not counted: " + describe(skipped) + "."));
            }
            int remaining = command.vials();
            for (VaccineBatch batch : fefo) {
                if (remaining == 0) {
                    break;
                }
                int take = Math.min(remaining, batch.getVialsOnHand());
                batch.issue(take);
                issues.save(new StockIssue(batch, site.getCode(), take, actor.getUsername(), now, true, null,
                        command.purpose()));
                allocations.add(new Allocation(batch.getId(), batch.getBatchCode(), batch.getLotNumber(),
                        batch.getExpiryDate(), take, batch.getVialsOnHand()));
                caches.batchChanged(batch.getId());
                remaining -= take;
            }
        }
        batches.flush(); // surfaces an @Version conflict here (-> HTTP 409) instead of at commit
        return new IssueResult(site.getCode(), command.product(), command.vials(), command.overrideBatchId() == null,
                allocations, skipped, auditId);
    }

    private Long issueWithOverride(IssueCommand command, AppUser actor, Site site, List<VaccineBatch> candidates,
            List<VaccineBatch> fefo, Instant now, LocalDate today, List<Allocation> allocations) {
        if (actor.getRole() != Role.DISTRICT_OFFICER) {
            throw new ForbiddenActionException("Only a district officer can override first-expiry-first-out order.");
        }
        if (command.overrideReason() == null || command.overrideReason().trim().length() < 10) {
            throw new BusinessRuleException("OVERRIDE_REASON_REQUIRED",
                    "An override needs a reason of at least 10 characters; it is recorded against your name.");
        }
        VaccineBatch chosen = candidates.stream().filter(b -> b.getId().equals(command.overrideBatchId())).findFirst()
                .orElseThrow(() -> new BusinessRuleException("NOT_AT_SITE", "Batch " + command.overrideBatchId()
                        + " is not " + command.product() + " stock held at " + site.getCode() + "."));
        if (chosen.getStatus() == BatchStatus.CONDEMNED) {
            throw new BusinessRuleException("BATCH_CONDEMNED", chosen.getBatchCode()
                    + " is CONDEMNED and has been withdrawn from issuable stock. No override can issue condemned stock.");
        }
        if (chosen.getStatus() == BatchStatus.AT_RISK) {
            throw new BusinessRuleException("BATCH_AT_RISK", chosen.getBatchCode()
                    + " is AT_RISK (quarantined). Release it after inspection before issuing.");
        }
        if (chosen.getExpiryDate().isBefore(today)) {
            throw new BusinessRuleException("BATCH_EXPIRED", chosen.getBatchCode() + " expired on " + chosen.getExpiryDate() + ".");
        }
        VaccineBatch fefoFirst = fefo.isEmpty() ? null : fefo.get(0);
        chosen.issue(command.vials());
        String details = "Issued " + command.vials() + " vials of " + command.product() + " from " + chosen.getBatchCode()
                + " (expiry " + chosen.getExpiryDate() + ") at " + site.getCode() + "; FEFO would have used "
                + (fefoFirst == null ? "no batch" : fefoFirst.getBatchCode() + " (expiry " + fefoFirst.getExpiryDate() + ")") + ".";
        OverrideAudit audit = overrides.save(new OverrideAudit(OverrideType.FEFO_OVERRIDE, chosen.getId(),
                chosen.getBatchCode(), actor, command.overrideReason().trim(), details, now));
        Long auditId = audit == null ? null : audit.getId();
        issues.save(new StockIssue(chosen, site.getCode(), command.vials(), actor.getUsername(), now, false, auditId,
                command.purpose()));
        allocations.add(new Allocation(chosen.getId(), chosen.getBatchCode(), chosen.getLotNumber(),
                chosen.getExpiryDate(), command.vials(), chosen.getVialsOnHand()));
        caches.batchChanged(chosen.getId());
        return auditId;
    }

    private Site site(Long siteId) {
        return sites.findById(siteId).orElseThrow(() -> new NotFoundException("No site " + siteId));
    }

    static WithdrawnBatch withdrawn(VaccineBatch b, LocalDate today) {
        String reason;
        if (b.getStatus() == BatchStatus.CONDEMNED) {
            reason = "CONDEMNED - withdrawn from issuable stock after a sustained temperature excursion";
        }
        else if (b.getStatus() == BatchStatus.AT_RISK) {
            reason = "AT_RISK - quarantined until a district officer reviews it";
        }
        else if (b.getExpiryDate().isBefore(today)) {
            reason = "EXPIRED on " + b.getExpiryDate();
        }
        else {
            reason = "No vials left";
        }
        return new WithdrawnBatch(b.getId(), b.getBatchCode(), b.getProduct(), b.getExpiryDate(), b.getStatus(),
                b.getVialsOnHand(), reason);
    }

    private static String describe(List<WithdrawnBatch> skipped) {
        return String.join(", ", skipped.stream().map(s -> s.batchCode() + " (" + s.status() + ")").toList());
    }
}
