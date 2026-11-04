package edu.nmamit.chainvax.batch;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import edu.nmamit.chainvax.audit.OverrideAudit;
import edu.nmamit.chainvax.audit.OverrideAuditRepository;
import edu.nmamit.chainvax.audit.OverrideType;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.common.CacheInvalidator;
import edu.nmamit.chainvax.common.NotFoundException;
import edu.nmamit.chainvax.telemetry.ExcursionWindowRepository;
import edu.nmamit.chainvax.user.AppUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The only place batch status changes: VIABLE -> AT_RISK -> CONDEMNED, plus audited officer release. */
@Service
public class BatchLifecycleService {

    private final BatchRepository batches;
    private final BatchStatusEventRepository events;
    private final ExcursionWindowRepository windows;
    private final OverrideAuditRepository overrides;
    private final CacheInvalidator caches;
    private final Clock clock;

    public BatchLifecycleService(BatchRepository batches, BatchStatusEventRepository events,
            ExcursionWindowRepository windows, OverrideAuditRepository overrides, CacheInvalidator caches, Clock clock) {
        this.batches = batches;
        this.events = events;
        this.windows = windows;
        this.overrides = overrides;
        this.caches = caches;
        this.clock = clock;
    }

    /**
     * Escalates without ever de-escalating. A jump straight from VIABLE to CONDEMNED is recorded as
     * two events so the timeline always shows the full lifecycle.
     */
    public List<StatusChange> escalate(VaccineBatch batch, BatchStatus target, String reason, String actor,
            Instant effectiveAt) {
        List<StatusChange> changes = new ArrayList<>();
        if (target.rank() <= batch.getStatus().rank()) {
            return changes;
        }
        if (batch.getStatus() == BatchStatus.VIABLE && target == BatchStatus.CONDEMNED) {
            changes.add(apply(batch, BatchStatus.AT_RISK, reason, actor, effectiveAt));
        }
        changes.add(apply(batch, target, reason, actor, effectiveAt));
        caches.batchChanged(batch.getId());
        return changes;
    }

    /** Officer releases a quarantined (AT_RISK) batch after inspection. Recorded as an override. */
    @Transactional
    public StatusChange release(long batchId, String reason, AppUser officer) {
        VaccineBatch batch = batches.findById(batchId).orElseThrow(() -> new NotFoundException("No batch " + batchId));
        if (batch.getStatus() != BatchStatus.AT_RISK) {
            throw new BusinessRuleException("NOT_AT_RISK", batch.getBatchCode() + " is " + batch.getStatus()
                    + "; only AT_RISK batches can be released. CONDEMNED is final.");
        }
        if (windows.findByBatchIdAndOpenTrue(batchId).isPresent()) {
            throw new BusinessRuleException("EXCURSION_STILL_OPEN", batch.getBatchCode()
                    + " still has an open excursion. Restore the temperature (an in-range reading closes it) before release.");
        }
        Instant now = clock.instant();
        StatusChange change = apply(batch, BatchStatus.VIABLE, "Released by officer after inspection: " + reason,
                officer.getUsername(), now);
        overrides.save(new OverrideAudit(OverrideType.STATUS_RELEASE, batch.getId(), batch.getBatchCode(), officer,
                reason, "Status AT_RISK -> VIABLE for " + batch.getBatchCode() + " (" + batch.getProduct() + ", lot "
                        + batch.getLotNumber() + ") at " + batch.getCurrentSite().getCode() + ".",
                now));
        caches.batchChanged(batch.getId());
        return change;
    }

    private StatusChange apply(VaccineBatch batch, BatchStatus to, String reason, String actor, Instant at) {
        BatchStatus from = batch.getStatus();
        events.save(new BatchStatusEvent(batch, from, to, reason, actor, batch.getCurrentSite().getCode(),
                batch.getCurrentCustodian(), at));
        batch.changeStatus(to, reason, at);
        return new StatusChange(batch.getId(), batch.getBatchCode(), from, to, reason, at);
    }
}
