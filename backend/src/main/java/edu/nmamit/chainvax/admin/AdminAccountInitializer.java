package edu.nmamit.chainvax.admin;

import edu.nmamit.chainvax.common.CurrentActor;
import edu.nmamit.chainvax.user.AppUser;
import edu.nmamit.chainvax.user.AppUserRepository;
import edu.nmamit.chainvax.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Makes sure an admin-console account exists (also on an existing Docker database, where the demo
 * seeder skips). Runs after the demo seeder ({@code @Order(10)}) so the seeder still sees an empty
 * user table on a fresh database. Override the demo credentials with CHAINVAX_ADMIN_PASSWORD.
 */
@Component
@Order(20)
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminAccountInitializer(AppUserRepository users, PasswordEncoder passwordEncoder,
            @Value("${chainvax.admin.username:admin}") String username,
            @Value("${chainvax.admin.password:admin1234}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        CurrentActor.runAs("system:admin-bootstrap", () -> users.save(new AppUser(username,
                passwordEncoder.encode(password), "System Administrator (demo)", Role.ADMIN, null)));
        log.info("Admin console account created: '{}' - sign in at /admin", username);
    }
}
