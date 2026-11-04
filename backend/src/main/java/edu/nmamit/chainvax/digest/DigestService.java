package edu.nmamit.chainvax.digest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.batch.BatchStatusEvent;
import edu.nmamit.chainvax.batch.BatchStatusEventRepository;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.config.ChainVaxProperties;
import edu.nmamit.chainvax.custody.LedgerHead;
import edu.nmamit.chainvax.custody.LedgerHeadRepository;
import edu.nmamit.chainvax.telemetry.ExcursionRules;
import edu.nmamit.chainvax.telemetry.ExcursionWindow;
import edu.nmamit.chainvax.telemetry.ExcursionWindowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the expiry + alert digest. "Dispatch" = stored in the in-app inbox and written to the log. */
@Service
public class DigestService {

    private static final Logger log = LoggerFactory.getLogger(DigestService.class);

    private final BatchRepository batches;
    private final BatchStatusEventRepository events;
    private final ExcursionWindowRepository windows;
    private final LedgerHeadRepository ledgerHeads;
    private final AlertDigestRepository digests;
    private final ChainVaxProperties properties;
    private final Clock clock;

    public DigestService(BatchRepository batches, BatchStatusEventRepository events, ExcursionWindowRepository windows,
            LedgerHeadRepository ledgerHeads, AlertDigestRepository digests, ChainVaxProperties properties, Clock clock) {
        this.batches = batches;
        this.events = events;
        this.windows = windows;
        this.ledgerHeads = ledgerHeads;
        this.digests = digests;
        this.properties = properties;
        this.clock = clock;
    }

    public record DigestView(Long id, Instant generatedAt, String triggerSource, String headline, String body,
            String ledgerHeadHash, long ledgerEntries, int openExcursions, int atRiskBatches, int condemnedBatches,
            int expiringSoon) {

        public static DigestView of(AlertDigest d) {
            return new DigestView(d.getId(), d.getGeneratedAt(), d.getTriggerSource(), d.getHeadline(), d.getBody(),
                    d.getLedgerHeadHash(), d.getLedgerEntries(), d.getOpenExcursions(), d.getAtRiskBatches(),
                    d.getCondemnedBatches(), d.getExpiringSoon());
        }
    }

    @Transactional
    public DigestView dispatch(String triggerSource) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        int warningDays = properties.worker().expiryWarningDays();
        List<VaccineBatch> all = batches.findAllByOrderByIdAsc();
        List<ExcursionWindow> open = windows.findByOpenTrue();
        Instant since = digests.findTopByOrderByIdDesc().map(AlertDigest::getGeneratedAt).orElse(Instant.EPOCH);
        List<BatchStatusEvent> recent = events.findByOccurredAtAfterOrderByIdAsc(since);
        LedgerHead head = ledgerHeads.findById(1).orElseThrow();

        List<String> lines = new ArrayList<>();
        lines.add("OPEN EXCURSIONS (" + open.size() + ")");
        for (ExcursionWindow w : open) {
            lines.add("- " + w.getBatch().getBatchCode() + ": " + w.getType() + " at " + w.getSite().getCode() + " for "
                    + ExcursionRules.human(w.exposure(now)) + ", peak " + w.getPeakTemperatureC().toPlainString()
                    + " C, custodian " + w.getCustodian());
        }
        lines.add("STATUS CHANGES SINCE LAST DIGEST (" + recent.size() + ")");
        for (BatchStatusEvent e : recent) {
            lines.add("- " + e.getBatch().getBatchCode() + ": " + e.getFromStatus() + " -> " + e.getToStatus() + " by "
                    + e.getActor() + " at " + e.getSiteCode());
        }
        List<VaccineBatch> expiring = all.stream()
                .filter(b -> b.getStatus() != BatchStatus.CONDEMNED && b.getVialsOnHand() > 0)
                .filter(b -> !b.getExpiryDate().isAfter(today.plusDays(warningDays)))
                .toList();
        lines.add("EXPIRING WITHIN " + warningDays + " DAYS (" + expiring.size() + ")");
        for (VaccineBatch b : expiring) {
            lines.add("- " + b.getBatchCode() + " (" + b.getProduct() + ") expires " + b.getExpiryDate() + ", "
                    + b.getVialsOnHand() + " vials at " + b.getCurrentSite().getCode());
        }
        lines.add("LEDGER HEAD #" + head.getLastSeq() + " " + head.getLastHash());

        int atRisk = (int) all.stream().filter(b -> b.getStatus() == BatchStatus.AT_RISK).count();
        int condemned = (int) all.stream().filter(b -> b.getStatus() == BatchStatus.CONDEMNED).count();
        String headline = open.size() + " open excursion(s), " + atRisk + " at-risk, " + condemned + " condemned, "
                + expiring.size() + " expiring within " + warningDays + " days";
        AlertDigest digest = digests.save(new AlertDigest(now, triggerSource, headline, String.join("\n", lines),
                head.getLastHash(), head.getLastSeq(), open.size(), atRisk, condemned, expiring.size()));
        log.info("Digest dispatched ({}): {}", triggerSource, headline);
        return DigestView.of(digest);
    }
}
