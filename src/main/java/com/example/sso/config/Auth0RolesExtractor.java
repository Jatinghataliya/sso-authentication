package com.example.sso.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracts roles from Auth0 JWT and maps them to Spring Security GrantedAuthority.
 *
 * Auth0 puts roles inside a custom namespace claim in the ID token, e.g.:
 *   "https://my-app.com/roles": ["admin", "user"]
 *
 * We also check the standard "roles" claim as a fallback.
 * Each role is mapped to ROLE_<UPPERCASE> so Spring Security hasRole() / hasAuthority() work.
 */
public class Auth0RolesExtractor {

    // The namespace you configure in Auth0 Actions/Rules — must match what you set in Auth0
    public static final String ROLES_CLAIM = "https://my-app.com/roles";

    /**
     * Extracts authorities from OIDC/OAuth2 user attributes, adding ROLE_X for each role found.
     */
    public static Set<GrantedAuthority> extractAuthorities(Collection<? extends GrantedAuthority> existingAuthorities) {
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>(existingAuthorities);

        for (GrantedAuthority authority : existingAuthorities) {
            Map<String, Object> attributes = null;

            if (authority instanceof OidcUserAuthority oidcAuth) {
                attributes = oidcAuth.getIdToken().getClaims();
            } else if (authority instanceof OAuth2UserAuthority oauthAuth) {
                attributes = oauthAuth.getAttributes();
            }

            if (attributes != null) {
                // Try namespaced claim first (Auth0 Actions), then plain "roles" fallback
                List<String> roles = getRolesFromClaims(attributes);
                for (String role : roles) {
                    mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                }
            }
        }
        return mappedAuthorities;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getRolesFromClaims(Map<String, Object> claims) {
        // Check namespaced claim (set via Auth0 Action)
        if (claims.containsKey(ROLES_CLAIM)) {
            Object val = claims.get(ROLES_CLAIM);
            if (val instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
        }
        // Fallback: plain "roles" claim
        if (claims.containsKey("roles")) {
            Object val = claims.get("roles");
            if (val instanceof List<?> list) {
                return list.stream().map(Object::toString).toList();
            }
        }
        return List.of();
    }
}
