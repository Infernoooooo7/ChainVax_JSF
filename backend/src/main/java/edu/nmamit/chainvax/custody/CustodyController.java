package edu.nmamit.chainvax.custody;

import java.util.List;

import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.custody.CustodyService.TransferCommand;
import edu.nmamit.chainvax.custody.CustodyService.TransferResult;
import edu.nmamit.chainvax.custody.LedgerService.VerificationResult;
import edu.nmamit.chainvax.user.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api")
@Tag(name = "Custody ledger")
public class CustodyController {

    private final CustodyService custody;
    private final LedgerService ledger;
    private final LedgerEntryRepository entries;
    private final CurrentUserService currentUser;

    public CustodyController(CustodyService custody, LedgerService ledger, LedgerEntryRepository entries,
            CurrentUserService currentUser) {
        this.custody = custody;
        this.ledger = ledger;
        this.entries = entries;
        this.currentUser = currentUser;
    }

    public record TransferRequest(@NotNull Long batchId, @NotNull Long toSiteId,
            @NotBlank @Size(max = 60) String toCustodian, @Size(max = 240) String note) {
    }

    @PostMapping("/custody/transfers")
    @PreAuthorize(Access.OPERATORS)
    @Operation(summary = "Record a custody hand-over; appends a SHA-256 chained ledger entry")
    public TransferResult transfer(@Valid @RequestBody TransferRequest request, Authentication authentication) {
        return custody.transfer(new TransferCommand(request.batchId(), request.toSiteId(), request.toCustodian(),
                request.note()), currentUser.require(authentication));
    }

    @GetMapping("/ledger")
    @PreAuthorize(Access.STAFF)
    @Transactional(readOnly = true)
    public List<LedgerEntryView> list(@RequestParam(required = false) Long batchId) {
        List<LedgerEntry> list = batchId == null ? entries.findAllByOrderBySeqAsc() : entries.findByBatchIdOrderBySeqAsc(batchId);
        return list.stream().map(LedgerEntryView::of).toList();
    }

    @GetMapping("/ledger/verify")
    @PreAuthorize(Access.STAFF)
    @Operation(summary = "Verify the whole custody chain (recomputes every SHA-256 link)")
    public VerificationResult verify() {
        return ledger.verify();
    }
}
