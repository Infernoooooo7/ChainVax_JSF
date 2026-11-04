package edu.nmamit.chainvax.stock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.site.Site;
import edu.nmamit.chainvax.site.SiteRepository;
import edu.nmamit.chainvax.stock.StockService.IssueCommand;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.AppUserRepository;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** @Version optimistic locking against real PostgreSQL. */
@SpringBootTest
@ActiveProfiles("test")
class OptimisticLockingIntegrationTest {

    @Autowired
    private BatchRepository batches;
    @Autowired
    private StockService stock;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private SiteRepository sites;
    @Autowired
    private StockIssueRepository issues;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void aWriteBasedOnAStaleVersionIsRejected() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long id = batches.findByBatchCode("CVX-PCV-001").orElseThrow().getId();

        VaccineBatch stale = tx.execute(status -> batches.findById(id).orElseThrow()); // snapshot at version v
        tx.executeWithoutResult(status -> batches.findById(id).orElseThrow().issue(1)); // someone else commits v+1
        stale.issue(1);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> batches.saveAndFlush(stale)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(batches.findById(id).orElseThrow().getVialsOnHand()).isEqualTo(59);
    }

    @Test
    void concurrentIssuesNeverOverAllocateStock() throws Exception {
        AppUser phcStorekeeper = users.findByUsername("store.phc").orElseThrow();
        Site phc = sites.findByCode("PHC-NTE").orElseThrow();
        VaccineBatch bopv = batches.findByBatchCode("CVX-BOPV-001").orElseThrow();
        int start = bopv.getVialsOnHand();
        long issuesBefore = issues.count();
        int threads = 8;
        int perRequest = 15; // 8 x 15 = 120 requested from 80 vials

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    stock.issue(new IssueCommand(phc.getId(), bopv.getProduct(), perRequest, "concurrency test", null, null),
                            phcStorekeeper);
                    return "OK";
                }
                catch (ObjectOptimisticLockingFailureException ex) {
                    return "CONCURRENT_UPDATE";
                }
                catch (BusinessRuleException ex) {
                    return ex.getCode();
                }
            }));
        }
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : futures) {
            outcomes.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        long ok = outcomes.stream().filter("OK"::equals).count();
        int after = batches.findById(bopv.getId()).orElseThrow().getVialsOnHand();
        assertThat(outcomes).allMatch(o -> o.equals("OK") || o.equals("CONCURRENT_UPDATE")
                || o.equals("INSUFFICIENT_ISSUABLE_STOCK") || o.equals("INSUFFICIENT_STOCK"));
        assertThat(ok).isBetween(1L, (long) (start / perRequest));
        assertThat(after).isEqualTo(start - (int) ok * perRequest).isGreaterThanOrEqualTo(0);
        assertThat(issues.count() - issuesBefore).isEqualTo(ok); // losers wrote nothing
    }
}
