package edu.nmamit.chainvax.user;

public enum Role {
    STOREKEEPER,
    TRANSPORTER,
    DISTRICT_OFFICER,
    AUDITOR,
    /** Admin console only: manages accounts and master data; cannot perform operational actions. */
    ADMIN
}
