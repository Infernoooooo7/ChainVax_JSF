package edu.nmamit.chainvax.auth;

import java.util.List;

import edu.nmamit.chainvax.auth.TokenService.TokenResponse;
import edu.nmamit.chainvax.auth.TokenService.UserInfo;
import edu.nmamit.chainvax.config.ChainVaxProperties;
import edu.nmamit.chainvax.demo.DemoDataSeeder;
import edu.nmamit.chainvax.telemetry.LoggerDevice;
import edu.nmamit.chainvax.telemetry.LoggerDeviceRepository;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.common.ForbiddenActionException;
import edu.nmamit.chainvax.user.AppUserRepository;
import edu.nmamit.chainvax.user.CurrentUserService;
import edu.nmamit.chainvax.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final LoggerDeviceRepository devices;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final CurrentUserService currentUser;
    private final ChainVaxProperties properties;

    public AuthController(AuthenticationManager authenticationManager, AppUserRepository users,
            LoggerDeviceRepository devices, PasswordEncoder passwordEncoder, TokenService tokens,
            CurrentUserService currentUser, ChainVaxProperties properties) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.devices = devices;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    public record LoginRequest(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 100) String password) {
    }

    public record DeviceTokenRequest(@NotBlank @Size(max = 40) String deviceCode,
            @NotBlank @Size(max = 100) String deviceSecret) {
    }

    public record DemoUser(String username, String fullName, String role, String siteCode, String siteName,
            String password, String summary) {
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Staff login - returns a JWT (Bearer) valid for 8 hours")
    @Transactional(readOnly = true)
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        AppUser user = users.findByUsername(authentication.getName()).orElseThrow();
        if (user.getRole() == Role.ADMIN) {
            throw new ForbiddenActionException("Administrator accounts sign in at /admin (the admin console), not the staff app.");
        }
        return tokens.issueUserToken(user);
    }

    @PostMapping("/device-token")
    @SecurityRequirements
    @Operation(summary = "Logger/device login - returns a JWT with role LOGGER for the ingestion API")
    @Transactional(readOnly = true)
    public TokenResponse deviceToken(@Valid @RequestBody DeviceTokenRequest request) {
        LoggerDevice device = devices.findByDeviceCode(request.deviceCode())
                .filter(d -> passwordEncoder.matches(request.deviceSecret(), d.getSecretHash()))
                .orElseThrow(() -> new BadCredentialsException("Unknown device or wrong secret"));
        return tokens.issueDeviceToken(device);
    }

    @GetMapping("/demo-users")
    @SecurityRequirements
    @Operation(summary = "Seeded demo accounts (only when demo seeding is enabled)")
    @Transactional(readOnly = true)
    public List<DemoUser> demoUsers() {
        if (!properties.demo().seed()) {
            return List.of();
        }
        return users.findAllByOrderByIdAsc().stream()
                .filter(u -> u.getRole() != Role.ADMIN)
                .map(u -> new DemoUser(u.getUsername(), u.getFullName(), u.getRole().name(), u.siteCode(),
                        u.getSite() == null ? null : u.getSite().getName(), DemoDataSeeder.DEMO_PASSWORD,
                        summary(u.getRole())))
                .toList();
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserInfo me(Authentication authentication) {
        return UserInfo.of(currentUser.require(authentication));
    }

    private static String summary(Role role) {
        switch (role) {
            case STOREKEEPER:
                return "Hands over and issues stock held at their own site";
            case TRANSPORTER:
                return "Hands over stock carried in their van";
            case DISTRICT_OFFICER:
                return "District-wide: FEFO overrides, AT_RISK release, policy changes";
            default:
                return "Read-only: ledger verification, audit trail, telemetry";
        }
    }
}
