package edu.nmamit.chainvax.custody;

public enum CustodyEventType {
    /** Batch received into the district store (start of its custody chain). */
    REGISTERED,
    /** Hand-over from one custodian/site to another. */
    TRANSFER
}
