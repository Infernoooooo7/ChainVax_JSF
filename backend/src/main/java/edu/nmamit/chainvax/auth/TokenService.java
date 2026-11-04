package edu.nmamit.chainvax.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import edu.nmamit.chainvax.config.ChainVaxProperties;
import edu.nmamit.chainvax.telemetry.LoggerDevice;
import edu.nmamit.chainvax.user.AppUser;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues HS256 JWTs (Spring Security's JwtEncoder / Nimbus) for staff users and logger devices. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final ChainVaxProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, ChainVaxProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record UserInfo(String username, String fullName, String role, String siteCode, String siteName) {

        public static UserInfo of(AppUser u) {
            return new UserInfo(u.getUsername(), u.getFullName(), u.getRole().name(), u.siteCode(),
                    u.getSite() == null ? null : u.getSite().getName());
        }
    }

    public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserInfo user) {
    }

    public TokenResponse issueUserToken(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().userTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("roles", List.of(user.getRole().name()))
                .claim("name", user.getFullName())
                .claim("site", user.siteCode() == null ? "" : user.siteCode())
                .build();
        return new TokenResponse(encode(claims), "Bearer", expiresAt, UserInfo.of(user));
    }

    public TokenResponse issueDeviceToken(LoggerDevice device) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().deviceTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject("device:" + device.getDeviceCode())
                .claim("roles", List.of("LOGGER"))
                .claim("device", device.getDeviceCode())
                .claim("site", device.getSite().getCode())
                .build();
        return new TokenResponse(encode(claims), "Bearer", expiresAt, null);
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
