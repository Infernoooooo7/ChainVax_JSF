package edu.nmamit.chainvax.admin;

import edu.nmamit.chainvax.auth.TokenService;
import edu.nmamit.chainvax.auth.TokenService.TokenResponse;
import edu.nmamit.chainvax.common.ForbiddenActionException;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.AppUserRepository;
import edu.nmamit.chainvax.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Separate login for the admin console. Only ADMIN accounts get a token here, and the staff
 * login ({@code /api/auth/login}) refuses ADMIN accounts - the two consoles never share a session.
 */
@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "Admin console")
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final TokenService tokens;

    public AdminAuthController(AuthenticationManager authenticationManager, AppUserRepository users,
            TokenService tokens) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.tokens = tokens;
    }

    public record AdminLoginRequest(@NotBlank @Size(max = 60) String username, @NotBlank @Size(max = 100) String password) {
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Administrator login (separate from the staff login) - returns an ADMIN JWT")
    @Transactional(readOnly = true)
    public TokenResponse login(@Valid @RequestBody AdminLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        AppUser user = users.findByUsername(authentication.getName()).orElseThrow();
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenActionException(
                    "This is not an administrator account. Staff sign in on the main ChainVax page.");
        }
        return tokens.issueUserToken(user);
    }
}
