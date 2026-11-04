package edu.nmamit.chainvax.telemetry;

import java.math.BigDecimal;
import java.time.Duration;

import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.policy.ExcursionPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExcursionRulesTest {

    /** Same thresholds as DEMO-FAST: heat >= 8.0 C condemns after 90 s, cold <= -0.5 C after 60 s. */
    private final ExcursionPolicy demo = new ExcursionPolicy("DEMO-FAST", "demo", "test", true,
            new BigDecimal("8.00"), new BigDecimal("-0.50"), 0, 90, 60);

    @Test
    void classifiesReadingsInclusiveOfAlarmThresholds() {
        assertThat(ExcursionRules.classify(new BigDecimal("8.00"), demo)).contains(ExcursionType.HEAT);
        assertThat(ExcursionRules.classify(new BigDecimal("7.99"), demo)).isEmpty();
        assertThat(ExcursionRules.classify(new BigDecimal("5.00"), demo)).isEmpty();
        assertThat(ExcursionRules.classify(new BigDecimal("-0.49"), demo)).isEmpty();
        assertThat(ExcursionRules.classify(new BigDecimal("-0.50"), demo)).contains(ExcursionType.COLD);
    }

    @Test
    void heatExcursionGoesAtRiskImmediatelyThenCondemnedAtThreshold() {
        assertThat(ExcursionRules.statusForExposure(ExcursionType.HEAT, Duration.ZERO, demo)).isEqualTo(BatchStatus.AT_RISK);
        assertThat(ExcursionRules.statusForExposure(ExcursionType.HEAT, Duration.ofSeconds(89), demo)).isEqualTo(BatchStatus.AT_RISK);
        assertThat(ExcursionRules.statusForExposure(ExcursionType.HEAT, Duration.ofSeconds(90), demo)).isEqualTo(BatchStatus.CONDEMNED);
    }

    @Test
    void coldExcursionUsesItsOwnShorterThreshold() {
        assertThat(ExcursionRules.statusForExposure(ExcursionType.COLD, Duration.ofSeconds(59), demo)).isEqualTo(BatchStatus.AT_RISK);
        assertThat(ExcursionRules.statusForExposure(ExcursionType.COLD, Duration.ofSeconds(60), demo)).isEqualTo(BatchStatus.CONDEMNED);
    }

    @Test
    void atRiskGracePeriodKeepsShortBlipsViable() {
        ExcursionPolicy tolerant = new ExcursionPolicy("T", "tolerant", "test", true, new BigDecimal("8.00"),
                new BigDecimal("-0.50"), 300, 36000, 3600);
        assertThat(ExcursionRules.statusForExposure(ExcursionType.HEAT, Duration.ofSeconds(120), tolerant)).isEqualTo(BatchStatus.VIABLE);
        assertThat(ExcursionRules.statusForExposure(ExcursionType.HEAT, Duration.ofSeconds(300), tolerant)).isEqualTo(BatchStatus.AT_RISK);
    }

    @Test
    void referencePolicyNeedsTenHoursOfHeatToCondemn() {
        ExcursionPolicy pqs = new ExcursionPolicy("PQS-E006-T1", "pqs", "test", false, new BigDecimal("8.00"),
                new BigDecimal("-0.50"), 0, 36000, 3600);
        assertThat(ExcursionRules.statusForExposure(ExcursionType.HEAT, Duration.ofHours(9), pqs)).isEqualTo(BatchStatus.AT_RISK);
        assertThat(ExcursionRules.statusForExposure(ExcursionType.HEAT, Duration.ofHours(10), pqs)).isEqualTo(BatchStatus.CONDEMNED);
        assertThat(ExcursionRules.statusForExposure(ExcursionType.COLD, Duration.ofMinutes(60), pqs)).isEqualTo(BatchStatus.CONDEMNED);
    }
}
