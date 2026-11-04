package edu.nmamit.chainvax.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.common.CurrentActor;
import edu.nmamit.chainvax.config.ChainVaxProperties;
import edu.nmamit.chainvax.custody.CustodyEventType;
import edu.nmamit.chainvax.custody.LedgerService;
import edu.nmamit.chainvax.policy.ExcursionPolicy;
import edu.nmamit.chainvax.policy.PolicyRepository;
import edu.nmamit.chainvax.site.Site;
import edu.nmamit.chainvax.site.SiteRepository;
import edu.nmamit.chainvax.telemetry.IngestionService;
import edu.nmamit.chainvax.telemetry.LoggerDevice;
import edu.nmamit.chainvax.telemetry.LoggerDeviceRepository;
import edu.nmamit.chainvax.telemetry.ReadingSource;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.AppUserRepository;
import edu.nmamit.chainvax.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds the small demo scenario (users, loggers, 7 batches, their custody chain and 30 minutes of
 * normal simulated readings). Custody entries go through {@link LedgerService} so the seeded chain
 * is hashed by exactly the same code as live transfers. All names are illustrative demo data.
 */
@Component
@ConditionalOnProperty(name = "chainvax.demo.seed", havingValue = "true")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "demo1234";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String PENTA = "Pentavalent (DTwP-HepB-Hib)";
    private static final String MR = "Measles-Rubella (MR)";

    private final AppUserRepository users;
    private final SiteRepository sites;
    private final PolicyRepository policies;
    private final BatchRepository batches;
    private final LoggerDeviceRepository devices;
    private final LedgerService ledger;
    private final IngestionService ingestion;
    private final PasswordEncoder passwordEncoder;
    private final ChainVaxProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public DemoDataSeeder(AppUserRepository users, SiteRepository sites, PolicyRepository policies,
            BatchRepository batches, LoggerDeviceRepository devices, LedgerService ledger, IngestionService ingestion,
            PasswordEncoder passwordEncoder, ChainVaxProperties properties, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.users = users;
        this.sites = sites;
        this.policies = policies;
        this.batches = batches;
        this.devices = devices;
        this.ledger = ledger;
        this.ingestion = ingestion;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.transactionManager = transactionManager;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("Demo data already present - skipping seed.");
            return;
        }
        CurrentActor.runAs("system:demo-seed", () -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> seedCore());
            seedReadingHistory();
        });
        log.info("Demo data seeded: 5 users (password '{}'), 3 sites, 3 loggers, 7 batches, custody ledger.", DEMO_PASSWORD);
    }

    private void seedCore() {
        Instant now = clock.instant();
        Site dvs = site("DVS-UDP");
        Site van = site("VAN-01");
        Site phc = site("PHC-NTE");
        ExcursionPolicy demo = policies.findByCode("DEMO-FAST").orElseThrow();
        ExcursionPolicy pqs = policies.findByCode("PQS-E006-T1").orElseThrow();

        String password = passwordEncoder.encode(DEMO_PASSWORD);
        users.saveAll(List.of(
                new AppUser("store.dvs", password, "Storekeeper, District Vaccine Store (demo)", Role.STOREKEEPER, dvs),
                new AppUser("driver.van01", password, "Transporter, cold-chain van 01 (demo)", Role.TRANSPORTER, van),
                new AppUser("store.phc", password, "Storekeeper, PHC Nitte (demo)", Role.STOREKEEPER, phc),
                new AppUser("officer", password, "District Immunization Officer (demo)", Role.DISTRICT_OFFICER, null),
                new AppUser("auditor", password, "Auditor (demo)", Role.AUDITOR, null)));

        String deviceSecretHash = passwordEncoder.encode(properties.simulator().deviceSecret());
        devices.saveAll(List.of(
                new LoggerDevice("LOG-DVS-01", dvs, deviceSecretHash, "Walk-in cooler logger, district store"),
                new LoggerDevice("LOG-VAN-01", van, deviceSecretHash, "Cold-box logger, van KA-20-CV-0101"),
                new LoggerDevice("LOG-PHC-NTE", phc, deviceSecretHash, "Ice-lined refrigerator logger, PHC Nitte")));

        // Every batch is first received into the district store (start of its custody chain).
        Instant received = now.minus(Duration.ofDays(12));
        List<VaccineBatch> seeded = new ArrayList<>();
        seeded.add(register("CVX-PENTA-001", PENTA, "DEMO-PV2405", "2027-05-31", 10, 200, dvs, demo, received, 0));
        seeded.add(register("CVX-PENTA-002", PENTA, "DEMO-PV2401", "2027-01-31", 10, 150, dvs, demo, received, 1));
        seeded.add(register("CVX-MR-001", MR, "DEMO-MR2402", "2027-02-28", 10, 120, dvs, demo, received, 2));
        seeded.add(register("CVX-MR-002", MR, "DEMO-MR2408", "2027-08-31", 10, 100, dvs, demo, received, 3));
        seeded.add(register("CVX-TD-001", "Td (tetanus-diphtheria)", "DEMO-TD2403", "2027-04-30", 10, 100, dvs, pqs, received, 4));
        seeded.add(register("CVX-BOPV-001", "bOPV (bivalent oral polio)", "DEMO-OP2311", "2026-12-20", 20, 80, dvs, demo, received, 5));
        seeded.add(register("CVX-PCV-001", "PCV (pneumococcal conjugate)", "DEMO-PC2404", "2027-03-31", 4, 60, dvs, demo, received, 6));

        VaccineBatch mr1 = seeded.get(2);
        VaccineBatch td1 = seeded.get(4);
        VaccineBatch bopv = seeded.get(5);
        VaccineBatch pcv = seeded.get(6);

        // Earlier outreach delivery: district store -> van -> PHC Nitte.
        Instant dispatched = now.minus(Duration.ofDays(3));
        transfer(bopv, dvs, van, "store.dvs", "driver.van01", "store.dvs", dispatched, "Loaded for PHC Nitte route");
        transfer(pcv, dvs, van, "store.dvs", "driver.van01", "store.dvs", dispatched.plusSeconds(300), "Loaded for PHC Nitte route");
        transfer(bopv, van, phc, "driver.van01", "store.phc", "driver.van01", dispatched.plus(Duration.ofHours(2)), "Delivered, ILR checked");
        transfer(pcv, van, phc, "driver.van01", "store.phc", "driver.van01", dispatched.plus(Duration.ofHours(2)).plusSeconds(120), "Delivered, ILR checked");

        // Today's run: two batches currently in the van.
        transfer(mr1, dvs, van, "store.dvs", "driver.van01", "store.dvs", now.minus(Duration.ofMinutes(40)), "Today's route to PHC Nitte");
        transfer(td1, dvs, van, "store.dvs", "driver.van01", "store.dvs", now.minus(Duration.ofMinutes(39)), "Today's route to PHC Nitte");
    }

    /** 27 minutes of normal SIMULATED readings per logger, through the real ingestion service. */
    private void seedReadingHistory() {
        Instant now = clock.instant();
        Random random = new Random(42);
        for (LoggerDevice device : devices.findAllByOrderByIdAsc()) {
            List<IngestionService.ReadingInput> history = new ArrayList<>();
            for (int minutesAgo = 30; minutesAgo >= 3; minutesAgo--) {
                BigDecimal t = BigDecimal.valueOf(4.2 + random.nextDouble() * 1.6).setScale(2, RoundingMode.HALF_UP);
                history.add(new IngestionService.ReadingInput(now.minus(Duration.ofMinutes(minutesAgo)), t));
            }
            ingestion.ingest(device.getDeviceCode(), history, ReadingSource.SIMULATOR);
        }
    }

    private VaccineBatch register(String code, String product, String lot, String expiry, int dosesPerVial, int vials,
            Site store, ExcursionPolicy policy, Instant receivedBase, int index) {
        Instant at = receivedBase.plus(Duration.ofMinutes(5L * index));
        VaccineBatch batch = batches.save(new VaccineBatch(code, product, lot, "Demo Manufacturer Ltd",
                LocalDate.parse(expiry), dosesPerVial, vials, store, "store.dvs", policy, at));
        ledger.append(batch, CustodyEventType.REGISTERED, null, store.getCode(), null, "store.dvs", "store.dvs",
                "Received from state store (demo)", at);
        return batch;
    }

    private void transfer(VaccineBatch batch, Site from, Site to, String fromCustodian, String toCustodian,
            String recordedBy, Instant at, String note) {
        batch.moveTo(to, toCustodian);
        ledger.append(batch, CustodyEventType.TRANSFER, from.getCode(), to.getCode(), fromCustodian, toCustodian,
                recordedBy, note, at);
    }

    private Site site(String code) {
        return sites.findByCode(code).orElseThrow(() -> new IllegalStateException("Missing reference site " + code));
    }
}
