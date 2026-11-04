package edu.nmamit.chainvax.stock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import edu.nmamit.chainvax.audit.OverrideAudit;
import edu.nmamit.chainvax.audit.OverrideAuditRepository;
import edu.nmamit.chainvax.audit.OverrideType;
import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.common.BusinessRuleException;
import edu.nmamit.chainvax.common.CacheInvalidator;
import edu.nmamit.chainvax.common.ForbiddenActionException;
import edu.nmamit.chainvax.policy.ExcursionPolicy;
import edu.nmamit.chainvax.site.Site;
import edu.nmamit.chainvax.site.SiteRepository;
import edu.nmamit.chainvax.site.SiteType;
import edu.nmamit.chainvax.stock.StockService.Allocation;
import edu.nmamit.chainvax.stock.StockService.IssueCommand;
import edu.nmamit.chainvax.stock.StockService.IssueResult;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FEFO allocator rules, with repositories mocked by Mockito. */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final String MR = "Measles-Rubella (MR)";
    private static final Instant NOW = Instant.parse("2026-11-05T06:00:00Z");

    @Mock
    private BatchRepository batches;
    @Mock
    private SiteRepository sites;
    @Mock
    private StockIssueRepository issues;
    @Mock
    private OverrideAuditRepository overrides;
    @Mock
    private CacheInvalidator caches;

    private StockService service;
    private Site store;
    private ExcursionPolicy policy;
    private AppUser officer;
    private AppUser storekeeper;

    @BeforeEach
    void setUp() {
        service = new StockService(batches, sites, issues, overrides, caches, Clock.fixed(NOW, ZoneOffset.UTC));
        store = new Site("DVS-UDP", "District store", SiteType.DISTRICT_STORE, "Udupi");
        ReflectionTestUtils.setField(store, "id", 1L);
        policy = new ExcursionPolicy("DEMO-FAST", "demo", "test", true, new BigDecimal("8.00"), new BigDecimal("-0.50"), 0, 90, 60);
        officer = new AppUser("officer", "x", "District officer", Role.DISTRICT_OFFICER, null);
        storekeeper = new AppUser("store.dvs", "x", "Storekeeper", Role.STOREKEEPER, store);
        lenient().when(sites.findById(1L)).thenReturn(Optional.of(store));
    }

    private VaccineBatch batch(long id, String code, String expiry, int vials) {
        VaccineBatch b = new VaccineBatch(code, MR, "LOT-" + id, "Demo", LocalDate.parse(expiry), 10, vials, store,
                "store.dvs", policy, NOW);
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }

    @Test
    void fefoIssuesFromEarliestExpiryFirstAndSkipsCondemnedStock() {
        VaccineBatch condemned = batch(10, "MR-OLDEST", "2027-01-31", 50);
        condemned.changeStatus(BatchStatus.CONDEMNED, "sustained heat excursion", NOW);
        VaccineBatch early = batch(11, "MR-EARLY", "2027-03-31", 30);
        VaccineBatch late = batch(12, "MR-LATE", "2027-08-31", 100);
        when(batches.findByCurrentSiteIdAndProductOrderByExpiryDateAscIdAsc(1L, MR)).thenReturn(List.of(condemned, early, late));

        IssueResult result = service.issue(new IssueCommand(1L, MR, 40, "session", null, null), storekeeper);

        assertThat(result.allocations()).extracting(Allocation::batchCode, Allocation::vials)
                .containsExactly(tuple("MR-EARLY", 30), tuple("MR-LATE", 10));
        assertThat(result.skipped()).extracting(StockService.WithdrawnBatch::batchCode).containsExactly("MR-OLDEST");
        assertThat(condemned.getVialsOnHand()).isEqualTo(50);
        assertThat(early.getVialsOnHand()).isZero();
        assertThat(late.getVialsOnHand()).isEqualTo(90);
        verify(issues, times(2)).save(any(StockIssue.class));
        verify(overrides, never()).save(any());
    }

    @Test
    void condemnedStockCannotBeIssuedEvenByAnOfficerOverride() {
        VaccineBatch condemned = batch(10, "MR-OLDEST", "2027-01-31", 50);
        condemned.changeStatus(BatchStatus.CONDEMNED, "sustained heat excursion", NOW);
        when(batches.findByCurrentSiteIdAndProductOrderByExpiryDateAscIdAsc(1L, MR)).thenReturn(List.of(condemned));

        assertThatThrownBy(() -> service.issue(new IssueCommand(1L, MR, 5, "session", 10L, "Officer insists on this lot"), officer))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CONDEMNED");
        assertThat(condemned.getVialsOnHand()).isEqualTo(50);
    }

    @Test
    void storekeeperCannotOverrideFefoOrder() {
        VaccineBatch early = batch(11, "MR-EARLY", "2027-03-31", 30);
        VaccineBatch late = batch(12, "MR-LATE", "2027-08-31", 100);
        when(batches.findByCurrentSiteIdAndProductOrderByExpiryDateAscIdAsc(1L, MR)).thenReturn(List.of(early, late));

        assertThatThrownBy(() -> service.issue(new IssueCommand(1L, MR, 5, "session", 12L, "Prefer the newer lot"), storekeeper))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void officerOverrideIsRecordedAgainstTheOfficer() {
        VaccineBatch early = batch(11, "MR-EARLY", "2027-03-31", 30);
        VaccineBatch late = batch(12, "MR-LATE", "2027-08-31", 100);
        when(batches.findByCurrentSiteIdAndProductOrderByExpiryDateAscIdAsc(1L, MR)).thenReturn(List.of(early, late));
        when(overrides.save(any(OverrideAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IssueResult result = service.issue(new IssueCommand(1L, MR, 20, "outreach", 12L, "Early lot reserved for outreach camp"), officer);

        ArgumentCaptor<OverrideAudit> audit = ArgumentCaptor.forClass(OverrideAudit.class);
        verify(overrides).save(audit.capture());
        assertThat(audit.getValue().getType()).isEqualTo(OverrideType.FEFO_OVERRIDE);
        assertThat(audit.getValue().getOfficerUsername()).isEqualTo("officer");
        assertThat(audit.getValue().getDetails()).contains("MR-LATE").contains("FEFO would have used MR-EARLY");
        assertThat(result.fefoOrder()).isFalse();
        assertThat(late.getVialsOnHand()).isEqualTo(80);
        assertThat(early.getVialsOnHand()).isEqualTo(30);
    }

    @Test
    void refusesToIssueMoreThanTheIssuableStock() {
        VaccineBatch atRisk = batch(13, "MR-QUARANTINED", "2027-02-28", 500);
        atRisk.changeStatus(BatchStatus.AT_RISK, "open excursion", NOW);
        VaccineBatch early = batch(11, "MR-EARLY", "2027-03-31", 30);
        when(batches.findByCurrentSiteIdAndProductOrderByExpiryDateAscIdAsc(1L, MR)).thenReturn(List.of(atRisk, early));

        assertThatThrownBy(() -> service.issue(new IssueCommand(1L, MR, 31, "session", null, null), storekeeper))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only 30 issuable vials")
                .hasMessageContaining("MR-QUARANTINED (AT_RISK)");
        assertThat(early.getVialsOnHand()).isEqualTo(30);
    }
}
