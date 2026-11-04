package edu.nmamit.chainvax.common;

import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** Name of whoever is acting right now: a username, "device:CODE", or a system worker. */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static String name() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return "system";
        }
        return auth.getName();
    }

    /** Runs background work (scheduled workers, seeding) under a named system identity. */
    public static void runAs(String actor, Runnable work) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            work.run();
        }
        finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
