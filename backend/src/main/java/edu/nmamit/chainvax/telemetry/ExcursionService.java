package edu.nmamit.chainvax.telemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import edu.nmamit.chainvax.batch.BatchLifecycleService;
import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.batch.StatusChange;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.common.CacheInvalidator;
import edu.nmamit.chainvax.policy.ExcursionPolicy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks excursion windows per batch and escalates batch status according to each batch's policy.
 * Two entry points: every ingested reading, and the scheduled worker re-evaluating open windows.
 */
@Service
public class ExcursionService {

    public static final String ENGINE = "system:excursion-engine";

    private final BatchRepository batches;
    private final ExcursionWindowRepository windows;
    private final BatchLifecycleService lifecycle;
    private final CacheInvalidator caches;
    private final Clock clock;

    public ExcursionService(BatchRepository batches, ExcursionWindowRepository windows, BatchLifecycleService lifecycle,
            CacheInvalidator caches, Clock clock) {
        this.batches = batches;
        this.windows = windows;
        this.lifecycle = lifecycle;
        this.caches = caches;
        this.clock = clock;
    }

    /** Applies one reading to every batch currently held at the reading's site. */
    @Transactional
    public List<StatusChange> applyReading(TemperatureReading reading) {
        List<StatusChange> changes = new ArrayList<>();
        for (VaccineBatch batch : batches.findByCurrentSiteId(reading.getSite().getId())) {
            changes.addAll(applyToBatch(batch, reading));
        }
        return changes;
    }

    private List<StatusChange> applyToBatch(VaccineBatch batch, TemperatureReading reading) {
        ExcursionPolicy policy = batch.getPolicy();
        Optional<ExcursionType> type = ExcursionRules.classify(reading.getTemperatureC(), policy);
        Optional<ExcursionWindow> open = windows.findByBatchIdAndOpenTrue(batch.getId());

        if (type.isEmpty()) {
            // Back in range: the excursion window ends at this reading.
            open.ifPresent(window -> {
                window.close(reading.getRecordedAt());
                caches.batchChanged(batch.getId());
            });
            return List.of();
        }

        ExcursionWindow window;
        if (open.isPresent() && open.get().getType() == type.get()) {
            window = open.get();
            window.extend(reading.getRecordedAt(), reading.getTemperatureC());
        }
        else {
            if (open.isPresent()) {
                open.get().close(reading.getRecordedAt());
                windows.flush(); // keep "one open window per batch" true before inserting the new one
            }
            window = windows.save(new ExcursionWindow(batch, reading.getSite(), reading.getDevice(),
                    batch.getCurrentCustodian(), type.get(), reading.getRecordedAt(), reading.getTemperatureC()));
            caches.batchChanged(batch.getId());
        }

        if (batch.getStatus() == BatchStatus.CONDEMNED) {
            return List.of();
        }
        Duration exposure = Duration.between(window.getStartedAt(), reading.getRecordedAt());
        String evidence = "logger " + reading.getDevice().getDeviceCode() + " read "
                + reading.getTemperatureC().toPlainString() + " C (" + reading.getSource() + ")";
        return evaluate(batch, window, exposure, reading.getRecordedAt(), evidence);
    }

    /**
     * Scheduled path: an open window keeps accruing exposure until an in-range reading arrives,
     * so a silent logger cannot hide a sustained excursion.
     */
    @Transactional
    public List<StatusChange> reevaluateOpenWindows() {
        Instant now = clock.instant();
        List<StatusChange> changes = new ArrayList<>();
        for (ExcursionWindow window : windows.findByOpenTrue()) {
            VaccineBatch batch = window.getBatch();
            if (batch.getStatus() == BatchStatus.CONDEMNED) {
                continue;
            }
            Duration exposure = window.exposure(now);
            String evidence = "scheduled re-evaluation: no in-range reading since " + window.getStartedAt();
            changes.addAll(evaluate(batch, window, exposure, now, evidence));
        }
        return changes;
    }

    private List<StatusChange> evaluate(VaccineBatch batch, ExcursionWindow window, Duration exposure, Instant at,
            String evidence) {
        ExcursionPolicy policy = batch.getPolicy();
        BatchStatus target = ExcursionRules.statusForExposure(window.getType(), exposure, policy);
        if (target.rank() <= batch.getStatus().rank()) {
            return List.of();
        }
        long threshold = target == BatchStatus.CONDEMNED
                ? ExcursionRules.condemnAfterSeconds(window.getType(), policy)
                : policy.getAtRiskAfterSeconds();
        String reason = window.getType() + " excursion at " + window.getSite().getCode() + " (custodian "
                + (window.getCustodian() == null ? "-" : window.getCustodian()) + ") sustained "
                + ExcursionRules.human(exposure) + ", peak " + window.getPeakTemperatureC().toPlainString()
                + " C; policy " + policy.getCode() + (target == BatchStatus.CONDEMNED ? " condemns" : " flags at-risk")
                + " at " + ExcursionRules.human(Duration.ofSeconds(threshold)) + ". Evidence: " + evidence;
        window.recordOutcome(target);
        return lifecycle.escalate(batch, target, reason, ENGINE, at);
    }
}
