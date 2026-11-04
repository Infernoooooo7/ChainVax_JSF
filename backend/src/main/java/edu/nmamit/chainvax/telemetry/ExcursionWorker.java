package edu.nmamit.chainvax.telemetry;

import java.util.List;

import edu.nmamit.chainvax.batch.StatusChange;
import edu.nmamit.chainvax.common.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code @Scheduled} worker that re-evaluates every open excursion window against wall-clock time. */
@Component
@ConditionalOnProperty(name = "chainvax.worker.enabled", havingValue = "true", matchIfMissing = true)
public class ExcursionWorker {

    private static final Logger log = LoggerFactory.getLogger(ExcursionWorker.class);

    private final ExcursionService excursions;

    public ExcursionWorker(ExcursionService excursions) {
        this.excursions = excursions;
    }

    @Scheduled(initialDelayString = "PT5S", fixedDelayString = "${chainvax.worker.reevaluate-interval}")
    public void reevaluateOpenWindows() {
        CurrentActor.runAs("system:excursion-worker", () -> {
            try {
                List<StatusChange> changes = excursions.reevaluateOpenWindows();
                changes.forEach(c -> log.warn("Excursion worker: {} {} -> {} ({})", c.batchCode(), c.from(), c.to(),
                        c.reason()));
            }
            catch (ObjectOptimisticLockingFailureException ex) {
                log.info("Excursion worker skipped a batch updated concurrently; it will retry on the next run.");
            }
        });
    }
}
