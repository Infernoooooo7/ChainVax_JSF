package edu.nmamit.chainvax.user;

import edu.nmamit.chainvax.common.ForbiddenActionException;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Resolves the authenticated JWT subject to a staff account for data-level checks. */
@Component
public class CurrentUserService {

    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) {
        this.users = users;
    }

    public AppUser require(Authentication authentication) {
        if (authentication == null) {
            throw new ForbiddenActionException("Not authenticated.");
        }
        return users.findByUsername(authentication.getName())
                .orElseThrow(() -> new ForbiddenActionException("This action requires a staff user account."));
    }
}
