package com.example.sso.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss dd-MMM-yyyy").withZone(ZoneId.systemDefault());

    /**
     * Protected dashboard — available to all authenticated users.
     * Passes roles and token expiry info to the template.
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OidcUser user,
                            @RegisteredOAuth2AuthorizedClient("okta") OAuth2AuthorizedClient authorizedClient,
                            Model model) {
        model.addAttribute("name",    user.getFullName());
        model.addAttribute("email",   user.getEmail());
        model.addAttribute("subject", user.getSubject());
        model.addAttribute("claims",  user.getClaims());

        // Roles
        List<String> roles = SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .toList();
        model.addAttribute("roles", roles);
        model.addAttribute("isAdmin", roles.contains("ADMIN"));

        // Token expiry info
        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            Instant expiresAt = authorizedClient.getAccessToken().getExpiresAt();
            if (expiresAt != null) {
                long secondsLeft = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
                model.addAttribute("tokenExpiresAt", FMT.format(expiresAt));
                model.addAttribute("tokenSecondsLeft", secondsLeft);
                model.addAttribute("tokenExpired", secondsLeft <= 0);
            }
        }

        // Refresh token present?
        boolean hasRefreshToken = authorizedClient != null
            && authorizedClient.getRefreshToken() != null;
        model.addAttribute("hasRefreshToken", hasRefreshToken);

        return "dashboard";
    }

    /**
     * Admin-only page — restricted by SecurityConfig (hasRole("ADMIN")).
     * Also protected at method level with @PreAuthorize as a second layer.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("name", user.getFullName());
        model.addAttribute("email", user.getEmail());
        return "admin";
    }

    /**
     * User-only page — restricted by SecurityConfig (hasRole("USER")).
     */
    @GetMapping("/user/profile")
    @PreAuthorize("hasRole('USER')")
    public String userProfile(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("name", user.getFullName());
        model.addAttribute("email", user.getEmail());
        return "user-profile";
    }

    /**
     * Access denied page — shown when a user tries to access a page they don't have a role for.
     */
    @GetMapping("/access-denied")
    public String accessDenied(@AuthenticationPrincipal OidcUser user, Model model) {
        if (user != null) model.addAttribute("name", user.getFullName());
        return "access-denied";
    }

    /**
     * REST endpoint — returns user info + roles as JSON.
     */
    @GetMapping("/api/me")
    @ResponseBody
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        List<String> roles = SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .toList();

        return Map.of(
            "subject", user.getSubject(),
            "name",    user.getFullName(),
            "email",   user.getEmail(),
            "roles",   roles,
            "claims",  user.getClaims()
        );
    }
}
