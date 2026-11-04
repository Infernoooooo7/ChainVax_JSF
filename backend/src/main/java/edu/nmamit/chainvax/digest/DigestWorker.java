package edu.nmamit.chainvax.digest;

import edu.nmamit.chainvax.common.CurrentActor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** {@code @Scheduled} expiry/alert digest dispatch. */
@Component
@ConditionalOnProperty(name = "chainvax.worker.enabled", havingValue = "true", matchIfMissing = true)
public class DigestWorker {

    private final DigestService digests;

    public DigestWorker(DigestService digests) {
        this.digests = digests;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${chainvax.worker.digest-interval}")
    public void dispatchDigest() {
        CurrentActor.runAs("system:digest-worker", () -> digests.dispatch("SCHEDULED"));
    }
}
