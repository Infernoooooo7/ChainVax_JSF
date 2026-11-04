package edu.nmamit.chainvax.custody;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

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
import edu.nmamit.chainvax.site.SiteType;
import edu.nmamit.chainvax.telemetry.ExcursionWindowRepository;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.AppUserRepository;
import edu.nmamit.chainvax.user.Role;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records custody hand-overs and appends each one to the hash-chained ledger in the same transaction. */
@Service
public class CustodyService {

    private final BatchRepository batches;
    private final SiteRepository sites;
    private final AppUserRepository users;
    private final ExcursionWindowRepository windows;
    private final LedgerService ledger;
    private final CacheInvalidator caches;
    private final Clock clock;

    public CustodyService(BatchRepository batches, SiteRepository sites, AppUserRepository users,
            ExcursionWindowRepository windows, LedgerService ledger, CacheInvalidator caches, Clock clock) {
        this.batches = batches;
        this.sites = sites;
        this.users = users;
        this.windows = windows;
        this.ledger = ledger;
        this.caches = caches;
        this.clock = clock;
    }

    public record TransferCommand(Long batchId, Long toSiteId, String toCustodian, String note) {
    }

    public record TransferResult(LedgerEntryView entry, BatchView batch) {
    }

    @Transactional
    public TransferResult transfer(TransferCommand command, AppUser actor) {
        VaccineBatch batch = batches.findById(command.batchId())
                .orElseThrow(() -> new NotFoundException("No batch " + command.batchId()));
        Site from = batch.getCurrentSite();
        Site to = sites.findById(command.toSiteId())
                .orElseThrow(() -> new NotFoundException("No site " + command.toSiteId()));

        // Data-level access: hands-on roles may only hand over stock held at their own site.
        if ((actor.getRole() == Role.STOREKEEPER || actor.getRole() == Role.TRANSPORTER) && !actor.isAssignedTo(from)) {
            throw new ForbiddenActionException(batch.getBatchCode() + " is held at " + from.getCode()
                    + "; only staff assigned there (or a district officer) can hand it over. You are assigned to "
                    + (actor.siteCode() == null ? "no site" : actor.siteCode()) + ".");
        }
        if (from.getId().equals(to.getId())) {
            throw new BusinessRuleException("SAME_SITE", batch.getBatchCode() + " is already at " + to.getCode() + ".");
        }
        AppUser receiver = users.findByUsername(command.toCustodian())
                .orElseThrow(() -> new NotFoundException("No user " + command.toCustodian()));
        if (receiver.getRole() == Role.AUDITOR) {
            throw new BusinessRuleException("INVALID_CUSTODIAN", "Auditors cannot take custody of stock.");
        }
        if (receiver.getSite() != null && !receiver.isAssignedTo(to)) {
            throw new BusinessRuleException("INVALID_CUSTODIAN", receiver.getUsername() + " is assigned to "
                    + receiver.siteCode() + ", not " + to.getCode() + ".");
        }
        if (batch.getStatus() == BatchStatus.CONDEMNED && to.getType() != SiteType.DISTRICT_STORE) {
            throw new BusinessRuleException("CONDEMNED_RETURN_ONLY", batch.getBatchCode()
                    + " is CONDEMNED and withdrawn from distribution: it can only be returned to a district store for disposal.");
        }

        Instant now = clock.instant();
        String fromCustodian = batch.getCurrentCustodian();
        batch.moveTo(to, receiver.getUsername());
        LedgerEntry entry = ledger.append(batch, CustodyEventType.TRANSFER, from.getCode(), to.getCode(),
                fromCustodian, receiver.getUsername(), actor.getUsername(), command.note(), now);
        caches.batchChanged(batch.getId());
        return new TransferResult(LedgerEntryView.of(entry),
                BatchView.of(batch, windows.findByBatchIdAndOpenTrue(batch.getId()).orElse(null), LocalDate.now(clock)));
    }
}
