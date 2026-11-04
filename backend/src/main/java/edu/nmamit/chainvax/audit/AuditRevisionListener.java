package edu.nmamit.chainvax.audit;

import edu.nmamit.chainvax.common.CurrentActor;
import org.hibernate.envers.RevisionListener;

/** Stamps every Envers revision with the authenticated actor. */
public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((AuditRevision) revisionEntity).setUsername(CurrentActor.name());
    }
}
