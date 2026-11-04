package edu.nmamit.chainvax.custody;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import edu.nmamit.chainvax.batch.VaccineBatch;
import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appends to and verifies the SHA-256 hash-chained custody ledger. */
@Service
public class LedgerService {

    private final EntityManager entityManager;
    private final LedgerEntryRepository entries;
    private final LedgerHeadRepository heads;
    private final Clock clock;

    public LedgerService(EntityManager entityManager, LedgerEntryRepository entries, LedgerHeadRepository heads,
            Clock clock) {
        this.entityManager = entityManager;
        this.entries = entries;
        this.heads = heads;
        this.clock = clock;
    }

    public record VerificationProblem(long seq, String code, String detail) {
    }

    public record VerificationResult(boolean valid, int entriesChecked, long headSeq, String headHash,
            Instant checkedAt, List<VerificationProblem> problems) {
    }

    @Transactional
    public LedgerEntry append(VaccineBatch batch, CustodyEventType type, String fromSiteCode, String toSiteCode,
            String fromCustodian, String toCustodian, String recordedBy, String note, Instant occurredAt) {
        LedgerHead head = heads.lockHead();
        long seq = head.getLastSeq() + 1;
        LedgerHasher.Fields fields = new LedgerHasher.Fields(seq, batch.getId(), batch.getBatchCode(), type,
                fromSiteCode, toSiteCode, fromCustodian, toCustodian, batch.getVialsOnHand(), batch.getStatus(),
                occurredAt.truncatedTo(ChronoUnit.MILLIS), recordedBy,
                note == null || note.isBlank() ? null : note.trim());
        String payload = LedgerHasher.canonicalPayload(fields);
        String hash = LedgerHasher.chainHash(head.getLastHash(), payload);
        LedgerEntry entry = new LedgerEntry(fields, payload, head.getLastHash(), hash);
        entityManager.persist(entry);
        head.advance(seq, hash);
        return entry;
    }

    /**
     * Recomputes the whole chain from the stored columns: detects edited fields (hash mismatch),
     * removed or reordered entries (broken link / sequence gap) and a truncated tail (head mismatch).
     */
    @Transactional(readOnly = true)
    public VerificationResult verify() {
        List<LedgerEntry> all = entries.findAllByOrderBySeqAsc();
        List<VerificationProblem> problems = new ArrayList<>();
        String expectedPrevious = LedgerHasher.GENESIS_HASH;
        long expectedSeq = 1;
        for (LedgerEntry entry : all) {
            if (entry.getSeq() != expectedSeq) {
                problems.add(new VerificationProblem(entry.getSeq(), "SEQUENCE_GAP",
                        "Expected entry #" + expectedSeq + " but found #" + entry.getSeq() + " - entries are missing."));
            }
            if (!entry.getPreviousHash().equals(expectedPrevious)) {
                problems.add(new VerificationProblem(entry.getSeq(), "BROKEN_LINK",
                        "previous_hash does not equal the hash of the preceding entry."));
            }
            String canonical = LedgerHasher.canonicalPayload(entry.fields());
            String recomputed = LedgerHasher.chainHash(entry.getPreviousHash(), canonical);
            if (!recomputed.equals(entry.getHash())) {
                problems.add(new VerificationProblem(entry.getSeq(), "HASH_MISMATCH",
                        "SHA-256(previous_hash + payload) recomputed from the stored fields does not match the stored hash - the entry was altered."));
            }
            expectedPrevious = entry.getHash();
            expectedSeq = entry.getSeq() + 1;
        }
        LedgerHead head = heads.findById(1).orElseThrow();
        if (head.getLastSeq() != expectedSeq - 1 || !head.getLastHash().equals(expectedPrevious)) {
            problems.add(new VerificationProblem(head.getLastSeq(), "HEAD_MISMATCH",
                    "The ledger head (#" + head.getLastSeq() + ") does not match the last stored entry - entries were removed from the end."));
        }
        return new VerificationResult(problems.isEmpty(), all.size(), head.getLastSeq(), head.getLastHash(),
                clock.instant(), problems);
    }
}
