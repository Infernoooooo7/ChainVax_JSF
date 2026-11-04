package edu.nmamit.chainvax.stock;

import java.time.Instant;
import java.util.List;

import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.stock.StockService.IssuableView;
import edu.nmamit.chainvax.stock.StockService.IssueCommand;
import edu.nmamit.chainvax.stock.StockService.IssueResult;
import edu.nmamit.chainvax.user.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
@Tag(name = "Stock (FEFO allocation)")
public class StockController {

    private final StockService stock;
    private final StockIssueRepository issues;
    private final CurrentUserService currentUser;

    public StockController(StockService stock, StockIssueRepository issues, CurrentUserService currentUser) {
        this.stock = stock;
        this.issues = issues;
        this.currentUser = currentUser;
    }

    public record IssueRequest(@NotNull Long siteId, @NotBlank @Size(max = 80) String product,
            @Min(1) @Max(10000) int vials, @Size(max = 160) String purpose, Long overrideBatchId,
            @Size(max = 500) String overrideReason) {
    }

    public record StockIssueView(Long id, String batchCode, String product, String siteCode, int vials,
            String issuedBy, Instant issuedAt, boolean fefoOrder, Long overrideAuditId, String purpose) {
    }

    @GetMapping("/issuable")
    @PreAuthorize(Access.STAFF)
    @Operation(summary = "Issuable stock at a site in FEFO order, plus withdrawn batches and why")
    public IssuableView issuable(@RequestParam Long siteId, @RequestParam(required = false) String product) {
        return stock.issuable(siteId, product);
    }

    @PostMapping("/issue")
    @PreAuthorize(Access.ISSUERS)
    @Operation(summary = "Issue vials using the FEFO allocator (officers may override FEFO with a reason)")
    public IssueResult issue(@Valid @RequestBody IssueRequest request, Authentication authentication) {
        return stock.issue(new IssueCommand(request.siteId(), request.product(), request.vials(), request.purpose(),
                request.overrideBatchId(), request.overrideReason()), currentUser.require(authentication));
    }

    @GetMapping("/issues")
    @PreAuthorize(Access.STAFF)
    @Transactional(readOnly = true)
    public List<StockIssueView> recentIssues() {
        return issues.findTop50ByOrderByIdDesc().stream()
                .map(i -> new StockIssueView(i.getId(), i.getBatch().getBatchCode(), i.getBatch().getProduct(),
                        i.getSiteCode(), i.getVials(), i.getIssuedBy(), i.getIssuedAt(), i.isFefoOrder(),
                        i.getOverrideAuditId(), i.getPurpose()))
                .toList();
    }
}
