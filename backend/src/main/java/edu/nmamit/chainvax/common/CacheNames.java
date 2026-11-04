package edu.nmamit.chainvax.common;

public final class CacheNames {

    /** Dashboard aggregates (counts by status, issuable vials, ledger head). */
    public static final String DASHBOARD = "dashboard";

    /** Per-batch "unit state" (status, location, custodian, stock). */
    public static final String UNIT_STATE = "unitState";

    private CacheNames() {
    }
}
