package edu.nmamit.chainvax.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code chainvax.*} settings in application.yml. */
@ConfigurationProperties(prefix = "chainvax")
public record ChainVaxProperties(Jwt jwt, EmbeddedDb embeddedDb, Demo demo, Simulator simulator, Worker worker) {

    public record Jwt(String secret, String issuer, Duration userTokenTtl, Duration deviceTokenTtl) {
    }

    public record EmbeddedDb(boolean enabled, int port, String dataDir, boolean cleanOnStart) {
    }

    public record Demo(boolean seed, boolean tamperEndpoints) {
    }

    public record Simulator(boolean autoStart, Duration interval, String baseUrl, String deviceSecret) {
    }

    public record Worker(boolean enabled, Duration reevaluateInterval, Duration digestInterval, int expiryWarningDays) {
    }
}
