package edu.nmamit.chainvax.telemetry;

import java.math.BigDecimal;
import java.time.Instant;

import edu.nmamit.chainvax.batch.BatchStatus;

public record WindowView(Long id, Long batchId, String batchCode, String siteCode, String deviceCode, String custodian,
        ExcursionType type, Instant startedAt, Instant lastReadingAt, Instant endedAt, BigDecimal peakTemperatureC,
        boolean open, long durationSeconds, BatchStatus outcome, String policyCode) {

    public static WindowView of(ExcursionWindow w, Instant now) {
        return new WindowView(w.getId(), w.getBatch().getId(), w.getBatch().getBatchCode(), w.getSite().getCode(),
                w.getDevice().getDeviceCode(), w.getCustodian(), w.getType(), w.getStartedAt(), w.getLastReadingAt(),
                w.getEndedAt(), w.getPeakTemperatureC(), w.isOpen(), w.exposure(now).getSeconds(), w.getOutcome(),
                w.getBatch().getPolicy().getCode());
    }
}
