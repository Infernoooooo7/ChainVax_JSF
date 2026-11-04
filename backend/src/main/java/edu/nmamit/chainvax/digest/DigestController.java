package edu.nmamit.chainvax.digest;

import java.util.List;

import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.digest.DigestService.DigestView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/digests")
@Tag(name = "Audit")
public class DigestController {

    private final DigestService digests;
    private final AlertDigestRepository repository;

    public DigestController(DigestService digests, AlertDigestRepository repository) {
        this.digests = digests;
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize(Access.STAFF)
    @Transactional(readOnly = true)
    public List<DigestView> latest() {
        return repository.findTop20ByOrderByIdDesc().stream().map(DigestView::of).toList();
    }

    @PostMapping("/run")
    @PreAuthorize(Access.OFFICER)
    @Operation(summary = "Dispatch an expiry/alert digest now (normally done by the @Scheduled worker)")
    public DigestView runNow() {
        return digests.dispatch("MANUAL");
    }
}
