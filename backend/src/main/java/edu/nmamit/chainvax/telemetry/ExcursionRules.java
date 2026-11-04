package edu.nmamit.chainvax.telemetry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.policy.ExcursionPolicy;

/** Pure excursion rules (no I/O) so they can be unit-tested exhaustively. */
public final class ExcursionRules {

    private ExcursionRules() {
    }

    /** HEAT if at/above the heat alarm, COLD if at/below the cold alarm, otherwise in range. */
    public static Optional<ExcursionType> classify(BigDecimal temperatureC, ExcursionPolicy policy) {
        if (temperatureC.compareTo(policy.getHeatAlarmC()) >= 0) {
            return Optional.of(ExcursionType.HEAT);
        }
        if (temperatureC.compareTo(policy.getColdAlarmC()) <= 0) {
            return Optional.of(ExcursionType.COLD);
        }
        return Optional.empty();
    }

    public static long condemnAfterSeconds(ExcursionType type, ExcursionPolicy policy) {
        return type == ExcursionType.HEAT ? policy.getHeatCondemnAfterSeconds() : policy.getColdCondemnAfterSeconds();
    }

    /** Status implied by one continuous excursion of the given type and length. */
    public static BatchStatus statusForExposure(ExcursionType type, Duration exposure, ExcursionPolicy policy) {
        long seconds = Math.max(0, exposure.getSeconds());
        if (seconds >= condemnAfterSeconds(type, policy)) {
            return BatchStatus.CONDEMNED;
        }
        if (seconds >= policy.getAtRiskAfterSeconds()) {
            return BatchStatus.AT_RISK;
        }
        return BatchStatus.VIABLE;
    }

    public static String human(Duration duration) {
        long s = Math.max(0, duration.getSeconds());
        if (s < 60) {
            return s + "s";
        }
        if (s < 3600) {
            return (s / 60) + "m " + (s % 60) + "s";
        }
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }
}
