package edu.nmamit.chainvax.custody;

import java.time.Instant;

import edu.nmamit.chainvax.batch.BatchStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerHasherTest {

    private final LedgerHasher.Fields fields = new LedgerHasher.Fields(4, 3L, "CVX-MR-001", CustodyEventType.TRANSFER,
            "DVS-UDP", "VAN-01", "store.dvs", "driver.van01", 120, BatchStatus.VIABLE,
            Instant.parse("2026-11-05T04:30:00.123Z"), "store.dvs", "Route \"A\" to PHC");

    @Test
    void sha256MatchesTheStandardTestVector() {
        // FIPS 180 example: SHA-256("abc")
        assertThat(LedgerHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void canonicalPayloadIsDeterministicOrderedJson() {
        String payload = LedgerHasher.canonicalPayload(fields);
        assertThat(payload).isEqualTo("{\"seq\":4,\"batchId\":3,\"batchCode\":\"CVX-MR-001\",\"eventType\":\"TRANSFER\","
                + "\"fromSite\":\"DVS-UDP\",\"toSite\":\"VAN-01\",\"fromCustodian\":\"store.dvs\",\"toCustodian\":\"driver.van01\","
                + "\"vials\":120,\"batchStatus\":\"VIABLE\",\"occurredAt\":\"2026-11-05T04:30:00.123Z\","
                + "\"recordedBy\":\"store.dvs\",\"note\":\"Route \\\"A\\\" to PHC\"}");
        assertThat(LedgerHasher.canonicalPayload(fields)).isEqualTo(payload);
    }

    @Test
    void entryHashIsSha256OfPreviousHashPlusPayload() {
        String payload = LedgerHasher.canonicalPayload(fields);
        String previous = LedgerHasher.GENESIS_HASH;
        assertThat(LedgerHasher.chainHash(previous, payload)).isEqualTo(LedgerHasher.sha256Hex(previous + payload));
        assertThat(LedgerHasher.chainHash(previous, payload)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void changingAnyHashedFieldOrThePreviousHashChangesTheHash() {
        String original = LedgerHasher.chainHash(LedgerHasher.GENESIS_HASH, LedgerHasher.canonicalPayload(fields));
        LedgerHasher.Fields moreVials = new LedgerHasher.Fields(4, 3L, "CVX-MR-001", CustodyEventType.TRANSFER,
                "DVS-UDP", "VAN-01", "store.dvs", "driver.van01", 620, BatchStatus.VIABLE,
                Instant.parse("2026-11-05T04:30:00.123Z"), "store.dvs", "Route \"A\" to PHC");
        assertThat(LedgerHasher.chainHash(LedgerHasher.GENESIS_HASH, LedgerHasher.canonicalPayload(moreVials)))
                .isNotEqualTo(original);
        assertThat(LedgerHasher.chainHash("1".repeat(64), LedgerHasher.canonicalPayload(fields))).isNotEqualTo(original);
    }
}
