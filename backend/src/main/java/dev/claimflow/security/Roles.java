package dev.claimflow.security;

import java.util.Set;

/** Keycloak realm roles. Spring sees each as an authority named ROLE_&lt;role&gt;. */
public final class Roles {

    public static final String CLAIMANT = "claimant";
    public static final String ADJUSTER = "adjuster";
    public static final String SENIOR_ADJUSTER = "senior-adjuster";
    public static final String FINANCE_MAKER = "finance-maker";
    public static final String FINANCE_CHECKER = "finance-checker";
    public static final String ADMIN = "admin";

    public static final Set<String> STAFF = Set.of(ADJUSTER, SENIOR_ADJUSTER, FINANCE_MAKER, FINANCE_CHECKER, ADMIN);

    /** SpEL for @PreAuthorize on staff-only endpoints. */
    public static final String STAFF_EXPR =
            "hasAnyRole('adjuster','senior-adjuster','finance-maker','finance-checker','admin')";

    private Roles() {
    }
}
