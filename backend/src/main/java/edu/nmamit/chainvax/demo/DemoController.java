package edu.nmamit.chainvax.demo;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.common.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEMO ONLY: simulates an attacker editing the ledger table directly in the database (bypassing
 * the application) so the presentation can show that chain verification detects it.
 * Disabled unless chainvax.demo.tamper-endpoints=true.
 */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "chainvax.demo.tamper-endpoints", havingValue = "true")
@PreAuthorize(Access.OFFICER)
@Tag(name = "Demo controls")
public class DemoController {

    private static final Pattern VIALS = Pattern.compile("\"vials\":(\\d+)");

    private final JdbcTemplate jdbc;

    public DemoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/ledger/{seq}/tamper")
    @Operation(summary = "DEMO: raw SQL edit of one ledger row (adds 500 vials) to show tamper detection")
    public Map<String, Object> tamper(@PathVariable long seq) {
        int updated = jdbc.update("update custody_ledger set vials = vials + 500 where seq = ?", seq);
        if (updated == 0) {
            throw new NotFoundException("No ledger entry #" + seq);
        }
        return Map.of("seq", seq, "tampered", true, "description",
                "Simulated rogue SQL: the vials column of entry #" + seq + " was increased by 500 directly in the database.");
    }

    @PostMapping("/ledger/{seq}/restore")
    @Operation(summary = "DEMO: undo the simulated edit (restores the value from the hashed payload)")
    public Map<String, Object> restore(@PathVariable long seq) {
        String payload;
        try {
            payload = jdbc.queryForObject("select payload from custody_ledger where seq = ?", String.class, seq);
        }
        catch (EmptyResultDataAccessException ex) {
            throw new NotFoundException("No ledger entry #" + seq);
        }
        Matcher matcher = VIALS.matcher(payload);
        if (!matcher.find()) {
            throw new IllegalStateException("Ledger payload has no vials field");
        }
        jdbc.update("update custody_ledger set vials = ? where seq = ?", Integer.parseInt(matcher.group(1)), seq);
        return Map.of("seq", seq, "restored", true);
    }
}
