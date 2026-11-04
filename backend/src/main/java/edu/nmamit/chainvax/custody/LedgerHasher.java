package edu.nmamit.chainvax.custody;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

import edu.nmamit.chainvax.batch.BatchStatus;

/**
 * Canonical payload + SHA-256 hash chaining for the custody ledger.
 * Hashing uses the JDK's standard {@link MessageDigest} SHA-256 implementation (FIPS 180-4);
 * no custom cryptography.
 */
public final class LedgerHasher {

    /** previous_hash of the first entry. */
    public static final String GENESIS_HASH = "0".repeat(64);

    private LedgerHasher() {
    }

    /** The fields covered by the hash, in a fixed order. */
    public record Fields(long seq, Long batchId, String batchCode, CustodyEventType eventType, String fromSiteCode,
            String toSiteCode, String fromCustodian, String toCustodian, int vials, BatchStatus batchStatus,
            Instant occurredAt, String recordedBy, String note) {
    }

    /** Deterministic JSON: fixed key order, no whitespace, ISO-8601 UTC timestamps. */
    public static String canonicalPayload(Fields f) {
        StringBuilder json = new StringBuilder(256).append('{');
        number(json, "seq", f.seq()).append(',');
        number(json, "batchId", f.batchId()).append(',');
        string(json, "batchCode", f.batchCode()).append(',');
        string(json, "eventType", f.eventType() == null ? null : f.eventType().name()).append(',');
        string(json, "fromSite", f.fromSiteCode()).append(',');
        string(json, "toSite", f.toSiteCode()).append(',');
        string(json, "fromCustodian", f.fromCustodian()).append(',');
        string(json, "toCustodian", f.toCustodian()).append(',');
        number(json, "vials", f.vials()).append(',');
        string(json, "batchStatus", f.batchStatus() == null ? null : f.batchStatus().name()).append(',');
        string(json, "occurredAt", f.occurredAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(f.occurredAt()))
                .append(',');
        string(json, "recordedBy", f.recordedBy()).append(',');
        string(json, "note", f.note());
        return json.append('}').toString();
    }

    /** hash = SHA-256(previous_hash + payload), lowercase hex. */
    public static String chainHash(String previousHash, String payload) {
        return sha256Hex(previousHash + payload);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every Java platform", ex);
        }
    }

    private static StringBuilder number(StringBuilder json, String key, Number value) {
        json.append('"').append(key).append("\":");
        return json.append(value == null ? "null" : value.toString());
    }

    private static StringBuilder string(StringBuilder json, String key, String value) {
        json.append('"').append(key).append("\":");
        if (value == null) {
            return json.append("null");
        }
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"');
    }
}
