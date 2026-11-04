package edu.nmamit.chainvax.dashboard;

import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.common.CacheNames;
import edu.nmamit.chainvax.config.ChainVaxProperties;
import edu.nmamit.chainvax.custody.LedgerHead;
import edu.nmamit.chainvax.custody.LedgerHeadRepository;
import edu.nmamit.chainvax.site.SiteRepository;
import edu.nmamit.chainvax.telemetry.ExcursionWindowRepository;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard aggregates. Cached; evicted after any status, custody, stock or excursion change. */
@Service
public class DashboardService {

    private final BatchRepository batches;
    private final SiteRepository sites;
    private final ExcursionWindowRepository windows;
    private final LedgerHeadRepository ledgerHeads;
    private final ChainVaxProperties properties;
    private final Clock clock;

    public DashboardService(BatchRepository batches, SiteRepository sites, ExcursionWindowRepository windows,
            LedgerHeadRepository ledgerHeads, ChainVaxProperties properties, Clock clock) {
        this.batches = batches;
        this.sites = sites;
        this.windows = windows;
        this.ledgerHeads = ledgerHeads;
        this.properties = properties;
        this.clock = clock;
    }

    public record SiteSummary(Long siteId, String siteCode, String siteName, String siteType, int batches, int viable,
            int atRisk, int condemned, int issuableVials) implements Serializable {
    }

    public record DashboardSummary(Instant generatedAt, int totalBatches, int viable, int atRisk, int condemned,
            int issuableVials, int withdrawnVials, long openExcursions, int expiringSoon, int expiryWarningDays,
            long ledgerEntries, String ledgerHeadHash, List<SiteSummary> sites) implements Serializable {
    }

    @Cacheable(CacheNames.DASHBOARD)
    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        LocalDate today = LocalDate.now(clock);
        int warningDays = properties.worker().expiryWarningDays();
        List<VaccineBatch> all = batches.findAllByOrderByIdAsc();
        int issuable = all.stream().filter(b -> b.isIssuableOn(today)).mapToInt(VaccineBatch::getVialsOnHand).sum();
        int withdrawn = all.stream().filter(b -> !b.isIssuableOn(today)).mapToInt(VaccineBatch::getVialsOnHand).sum();
        int expiring = (int) all.stream()
                .filter(b -> b.getStatus() != BatchStatus.CONDEMNED && b.getVialsOnHand() > 0)
                .filter(b -> !b.getExpiryDate().isAfter(today.plusDays(warningDays)))
                .count();
        List<SiteSummary> perSite = sites.findAllByOrderByIdAsc().stream().map(site -> {
            List<VaccineBatch> here = all.stream().filter(b -> b.getCurrentSite().getId().equals(site.getId())).toList();
            return new SiteSummary(site.getId(), site.getCode(), site.getName(), site.getType().name(), here.size(),
                    count(here, BatchStatus.VIABLE), count(here, BatchStatus.AT_RISK), count(here, BatchStatus.CONDEMNED),
                    here.stream().filter(b -> b.isIssuableOn(today)).mapToInt(VaccineBatch::getVialsOnHand).sum());
        }).toList();
        LedgerHead head = ledgerHeads.findById(1).orElseThrow();
        return new DashboardSummary(clock.instant(), all.size(), count(all, BatchStatus.VIABLE),
                count(all, BatchStatus.AT_RISK), count(all, BatchStatus.CONDEMNED), issuable, withdrawn,
                windows.countByOpenTrue(), expiring, warningDays, head.getLastSeq(), head.getLastHash(), perSite);
    }

    private static int count(List<VaccineBatch> list, BatchStatus status) {
        return (int) list.stream().filter(b -> b.getStatus() == status).count();
    }
}
