package edu.nmamit.chainvax.user;

import java.util.List;

import edu.nmamit.chainvax.common.Access;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize(Access.STAFF)
@Tag(name = "Reference data")
public class UserController {

    private final AppUserRepository users;

    public UserController(AppUserRepository users) {
        this.users = users;
    }

    public record StaffView(String username, String fullName, String role, String siteCode) {
    }

    /** Staff directory (used to pick the receiving custodian of a transfer). */
    @GetMapping
    public List<StaffView> list() {
        return users.findAllByOrderByIdAsc().stream()
                .filter(u -> u.getRole() != Role.ADMIN) // admin-console accounts are not staff
                .map(u -> new StaffView(u.getUsername(), u.getFullName(), u.getRole().name(), u.siteCode()))
                .toList();
    }
}
