package fr.pacpilot.server.interventions.domain;

/**
 * Where a visit stands. The V1 set: {@code PLANNED → DONE | CANCELLED | RESCHEDULED | NO_SHOW}.
 *
 * <p><b>Deliberately open.</b> V1.5 adds the booking-link entry path — {@code REQUESTED → CONFIRMED
 * | DECLINED} — in front of this set, and §14 requires that to be an <i>addition</i> rather than a
 * migration of meaning. The database stores this as text with a check constraint rather than as a
 * Postgres enum type for the same reason: adding a member is one line of DDL, not a rewrite of every
 * dependent object.
 *
 * <p><b>Nothing is ever auto-confirmed.</b> An appointment the artisan did not accept makes the whole
 * timeline untrustworthy, which is the one thing this context cannot afford to be.
 */
public enum InterventionStatus {
    PLANNED,
    DONE,
    CANCELLED,
    RESCHEDULED,
    NO_SHOW;

    /** A visit that did not happen records why. */
    public boolean requiresReason() {
        return this == CANCELLED || this == NO_SHOW;
    }
}
