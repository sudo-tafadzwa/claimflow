package dev.claimflow.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller: a username and the realm roles from the token. */
public record CurrentUser(String username, Set<String> roles) {

    public static CurrentUser from(Authentication auth) {
        String username = auth.getPrincipal() instanceof Jwt jwt && jwt.getClaimAsString("preferred_username") != null
                ? jwt.getClaimAsString("preferred_username")
                : auth.getName();
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(username, roles);
    }

    public boolean has(String role) {
        return roles.contains(role);
    }

    public boolean isStaff() {
        return roles.stream().anyMatch(Roles.STAFF::contains);
    }
}
