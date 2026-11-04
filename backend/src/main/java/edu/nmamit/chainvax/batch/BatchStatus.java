package edu.nmamit.chainvax.batch;

/** Batch lifecycle. Transitions only escalate, except an audited officer release AT_RISK -> VIABLE. */
public enum BatchStatus {
    VIABLE(0),
    AT_RISK(1),
    CONDEMNED(2);

    private final int rank;

    BatchStatus(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
