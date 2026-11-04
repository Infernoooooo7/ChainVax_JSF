package edu.nmamit.chainvax.audit;

import java.time.Instant;
import java.util.List;

import edu.nmamit.chainvax.batch.BatchStatusEventRepository;
import edu.nmamit.chainvax.batch.StatusEventView;
import edu.nmamit.chainvax.batch.VaccineBatch;
import edu.nmamit.chainvax.common.Access;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize(Access.STAFF)
@Tag(name = "Audit")
public class AuditController {

    private final OverrideAuditRepository overrides;
    private final BatchStatusEventRepository events;
    private final EntityManager entityManager;

    public AuditController(OverrideAuditRepository overrides, BatchStatusEventRepository events,
            EntityManager entityManager) {
        this.overrides = overrides;
        this.events = events;
        this.entityManager = entityManager;
    }

    public record RevisionView(Long revision, Instant at, String username, RevisionType revisionType, String status,
            int vialsOnHand, String siteCode, String custodian, String statusReason) {
    }

    @GetMapping("/overrides")
    @Transactional(readOnly = true)
    @Operation(summary = "Officer-attributed overrides (FEFO bypass, AT_RISK release, policy change)")
    public List<OverrideView> overrides() {
        return overrides.findTop100ByOrderByIdDesc().stream().map(OverrideView::of).toList();
    }

    @GetMapping("/status-events")
    @Transactional(readOnly = true)
    @Operation(summary = "Recent lifecycle transitions with the site and custodian at the time")
    public List<StatusEventView> statusEvents() {
        return events.findTop100ByOrderByIdDesc().stream().map(StatusEventView::of).toList();
    }

    @GetMapping("/batches/{id}/revisions")
    @Transactional(readOnly = true)
    @Operation(summary = "Hibernate Envers revision history of one batch (who changed what)")
    public List<RevisionView> batchRevisions(@PathVariable Long id) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = reader.createQuery()
                .forRevisionsOfEntity(VaccineBatch.class, false, true)
                .add(AuditEntity.id().eq(id))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(row -> {
            VaccineBatch snapshot = (VaccineBatch) row[0];
            AuditRevision revision = (AuditRevision) row[1];
            RevisionType type = (RevisionType) row[2];
            return new RevisionView(revision.getId(), Instant.ofEpochMilli(revision.getTimestamp()),
                    revision.getUsername(), type, snapshot.getStatus() == null ? null : snapshot.getStatus().name(),
                    snapshot.getVialsOnHand(),
                    snapshot.getCurrentSite() == null ? null : snapshot.getCurrentSite().getCode(),
                    snapshot.getCurrentCustodian(), snapshot.getStatusReason());
        }).toList();
    }
}
