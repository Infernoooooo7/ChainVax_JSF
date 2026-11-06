package edu.nmamit.chainvax.common;

/** Method-security expressions shared by controllers (role-based access control). */
public final class Access {

    /** Any human user: storekeeper, transporter, district officer or auditor (read access). */
    public static final String STAFF = "hasAnyRole('STOREKEEPER','TRANSPORTER','DISTRICT_OFFICER','AUDITOR')";

    /** Users who physically handle stock (auditors are read-only). */
    public static final String OPERATORS = "hasAnyRole('STOREKEEPER','TRANSPORTER','DISTRICT_OFFICER')";

    /** Users who may issue stock to vaccination sessions. */
    public static final String ISSUERS = "hasAnyRole('STOREKEEPER','DISTRICT_OFFICER')";

    public static final String OFFICER = "hasRole('DISTRICT_OFFICER')";

    /** Admin console: account and master-data management (separate login, no operational actions). */
    public static final String ADMIN = "hasRole('ADMIN')";

    /** Device tokens issued to temperature loggers (and the simulator acting as one). */
    public static final String LOGGER = "hasRole('LOGGER')";

    private Access() {
    }
}
