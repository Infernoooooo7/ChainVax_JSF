package edu.nmamit.chainvax;

import edu.nmamit.chainvax.batch.BatchRepository;
import edu.nmamit.chainvax.custody.LedgerService;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same migrations + seed against a throw-away PostgreSQL container (Testcontainers).
 * Skipped automatically when Docker is not available on the machine.
 */
@SpringBootTest(properties = "chainvax.embedded-db.enabled=false")
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TestcontainersPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private BatchRepository batches;

    @Autowired
    private LedgerService ledger;

    @Test
    void migrationsSeedAndLedgerVerifyOnAPostgresContainer() {
        assertThat(batches.count()).isEqualTo(7);
        assertThat(ledger.verify().valid()).isTrue();
    }
}
