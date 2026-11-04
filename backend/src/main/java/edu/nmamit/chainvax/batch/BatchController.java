package edu.nmamit.chainvax.batch;

import java.util.List;

import edu.nmamit.chainvax.batch.BatchQueryService.BatchDetail;
import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.user.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batches")
@Tag(name = "Batches")
public class BatchController {

    private final BatchQueryService queries;
    private final BatchLifecycleService lifecycle;
    private final CurrentUserService currentUser;

    public BatchController(BatchQueryService queries, BatchLifecycleService lifecycle, CurrentUserService currentUser) {
        this.queries = queries;
        this.lifecycle = lifecycle;
        this.currentUser = currentUser;
    }

    public record ReleaseRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
    }

    @GetMapping
    @PreAuthorize(Access.STAFF)
    public List<BatchView> list(@RequestParam(required = false) Long siteId,
            @RequestParam(required = false) BatchStatus status) {
        return queries.list(siteId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Access.STAFF)
    @Operation(summary = "Batch detail: lifecycle events, excursion windows, custody ledger entries and overrides")
    public BatchDetail detail(@PathVariable Long id) {
        return queries.detail(id);
    }

    @GetMapping("/{id}/state")
    @PreAuthorize(Access.STAFF)
    @Operation(summary = "Cached unit state of one batch")
    public BatchView state(@PathVariable Long id) {
        return queries.state(id);
    }

    @PostMapping("/{id}/release")
    @PreAuthorize(Access.OFFICER)
    @Operation(summary = "Release an AT_RISK batch back to VIABLE after inspection (district officer, audited)")
    public StatusChange release(@PathVariable Long id, @Valid @RequestBody ReleaseRequest request,
            Authentication authentication) {
        return lifecycle.release(id, request.reason(), currentUser.require(authentication));
    }
}
