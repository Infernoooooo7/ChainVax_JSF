package edu.nmamit.chainvax.batch;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.nmamit.chainvax.audit.OverrideAuditRepository;
import edu.nmamit.chainvax.audit.OverrideView;
import edu.nmamit.chainvax.common.CacheNames;
import edu.nmamit.chainvax.common.NotFoundException;
import edu.nmamit.chainvax.custody.LedgerEntryRepository;
import edu.nmamit.chainvax.custody.LedgerEntryView;
import edu.nmamit.chainvax.telemetry.ExcursionWindow;
import edu.nmamit.chainvax.telemetry.ExcursionWindowRepository;
import edu.nmamit.chainvax.telemetry.WindowView;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchQueryService {

    private final BatchRepository batches;
    private final ExcursionWindowRepository windows;
    private final BatchStatusEventRepository events;
    private final LedgerEntryRepository ledger;
    private final OverrideAuditRepository overrides;
    private final Clock clock;

    public BatchQueryService(BatchRepository batches, ExcursionWindowRepository windows,
            BatchStatusEventRepository events, LedgerEntryRepository ledger, OverrideAuditRepository overrides,
            Clock clock) {
        this.batches = batches;
        this.windows = windows;
        this.events = events;
        this.ledger = ledger;
        this.overrides = overrides;
        this.clock = clock;
    }

    public record BatchDetail(BatchView batch, List<StatusEventView> events, List<WindowView> excursions,
            List<LedgerEntryView> custody, List<OverrideView> overrides) {
    }

    @Transactional(readOnly = true)
    public List<BatchView> list(Long siteId, BatchStatus status) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, ExcursionWindow> open = openWindowsByBatch();
        return batches.findAllByOrderByIdAsc().stream()
                .filter(b -> siteId == null || b.getCurrentSite().getId().equals(siteId))
                .filter(b -> status == null || b.getStatus() == status)
                .map(b -> BatchView.of(b, open.get(b.getId()), today))
                .toList();
    }

    /** Frequently read unit state - cached (in memory, or in Redis with the "redis" profile). */
    @Cacheable(cacheNames = CacheNames.UNIT_STATE, key = "#batchId")
    @Transactional(readOnly = true)
    public BatchView state(Long batchId) {
        VaccineBatch batch = batches.findById(batchId).orElseThrow(() -> new NotFoundException("No batch " + batchId));
        ExcursionWindow open = windows.findByBatchIdAndOpenTrue(batchId).orElse(null);
        return BatchView.of(batch, open, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public BatchDetail detail(Long batchId) {
        VaccineBatch batch = batches.findById(batchId).orElseThrow(() -> new NotFoundException("No batch " + batchId));
        Instant now = clock.instant();
        ExcursionWindow open = windows.findByBatchIdAndOpenTrue(batchId).orElse(null);
        return new BatchDetail(
                BatchView.of(batch, open, LocalDate.now(clock)),
                events.findByBatchIdOrderByIdAsc(batchId).stream().map(StatusEventView::of).toList(),
                windows.findByBatchIdOrderByStartedAtDesc(batchId).stream().map(w -> WindowView.of(w, now)).toList(),
                ledger.findByBatchIdOrderBySeqAsc(batchId).stream().map(LedgerEntryView::of).toList(),
                overrides.findByBatchIdOrderByIdDesc(batchId).stream().map(OverrideView::of).toList());
    }

    private Map<Long, ExcursionWindow> openWindowsByBatch() {
        return windows.findByOpenTrue().stream()
                .collect(Collectors.toMap(w -> w.getBatch().getId(), Function.identity(), (a, b) -> a));
    }
}
