package edu.nmamit.chainvax.batch;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import edu.nmamit.chainvax.telemetry.ExcursionRules;
import edu.nmamit.chainvax.telemetry.ExcursionWindow;

/** Read model of a batch ("unit state"). Serializable so it can be cached in Redis. */
public record BatchView(
        Long id,
        String batchCode,
        String product,
        String lotNumber,
        String manufacturer,
        LocalDate expiryDate,
        int dosesPerVial,
        int initialVials,
        int vialsOnHand,
        BatchStatus status,
        String statusReason,
        Instant statusChangedAt,
        Long siteId,
        String siteCode,
        String siteName,
        String siteType,
        String custodian,
        String policyCode,
        String policyName,
        boolean policyDemoOnly,
        long version,
        boolean issuable,
        OpenExcursion openExcursion) implements Serializable {

    /** Present while the batch has an open excursion window; the UI counts down to condemnation. */
    public record OpenExcursion(Long windowId, String type, Instant startedAt, BigDecimal peakTemperatureC,
            String siteCode, String custodian, long condemnAfterSeconds, long atRiskAfterSeconds)
            implements Serializable {
    }

    public static BatchView of(VaccineBatch b, ExcursionWindow openWindow, LocalDate today) {
        OpenExcursion excursion = null;
        if (openWindow != null) {
            excursion = new OpenExcursion(openWindow.getId(), openWindow.getType().name(), openWindow.getStartedAt(),
                    openWindow.getPeakTemperatureC(), openWindow.getSite().getCode(), openWindow.getCustodian(),
                    ExcursionRules.condemnAfterSeconds(openWindow.getType(), b.getPolicy()),
                    b.getPolicy().getAtRiskAfterSeconds());
        }
        return new BatchView(b.getId(), b.getBatchCode(), b.getProduct(), b.getLotNumber(), b.getManufacturer(),
                b.getExpiryDate(), b.getDosesPerVial(), b.getInitialVials(), b.getVialsOnHand(), b.getStatus(),
                b.getStatusReason(), b.getStatusChangedAt(), b.getCurrentSite().getId(), b.getCurrentSite().getCode(),
                b.getCurrentSite().getName(), b.getCurrentSite().getType().name(), b.getCurrentCustodian(),
                b.getPolicy().getCode(), b.getPolicy().getName(), b.getPolicy().isDemoOnly(), b.getVersion(),
                b.isIssuableOn(today), excursion);
    }
}
