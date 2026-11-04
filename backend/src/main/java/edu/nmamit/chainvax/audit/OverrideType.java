package edu.nmamit.chainvax.audit;

public enum OverrideType {
    /** Officer issued stock out of first-expiry-first-out order. */
    FEFO_OVERRIDE,
    /** Officer released an AT_RISK batch back to VIABLE after inspection. */
    STATUS_RELEASE,
    /** Officer changed an excursion policy. */
    POLICY_CHANGE
}
