package com.example.sso.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
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

    private final OAuth2AuthorizedClientService authorizedClientService;

    public DashboardController(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    /**
     * Protected dashboard — works for both Auth0 (OidcUser) and GitHub (OAuth2User).
     * Detects which provider was used and extracts user info accordingly.
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OAuth2User oauth2User, Model model) {

        // Detect provider from authentication token
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication();
        String provider = authToken.getAuthorizedClientRegistrationId(); // "okta" or "github"
        model.addAttribute("provider", provider);

        // Extract user info — OidcUser (Auth0) vs OAuth2User (GitHub)
        if (oauth2User instanceof OidcUser oidcUser) {
            // Auth0 path
            model.addAttribute("name",    oidcUser.getFullName());
            model.addAttribute("email",   oidcUser.getEmail());
            model.addAttribute("subject", oidcUser.getSubject());
            model.addAttribute("claims",  oidcUser.getClaims());
        } else {
            // GitHub path — attributes come from the GitHub userinfo API
            String name    = oauth2User.getAttribute("name");
            String login   = oauth2User.getAttribute("login");   // GitHub username
            String email   = oauth2User.getAttribute("email");
            Object idObj   = oauth2User.getAttribute("id");      // Integer from GitHub API
            String subject = idObj != null ? idObj.toString() : login;

            model.addAttribute("name",    name != null ? name : login);
            model.addAttribute("email",   email != null ? email : login + "@github");
            model.addAttribute("subject", subject);
            model.addAttribute("claims",  oauth2User.getAttributes());
            model.addAttribute("githubLogin",   login);
            model.addAttribute("githubAvatar",  oauth2User.getAttribute("avatar_url"));
            model.addAttribute("githubProfile", oauth2User.getAttribute("html_url"));
        }

        // Roles (from Spring Security authorities — same for both providers)
        List<String> roles = SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .toList();
        model.addAttribute("roles", roles);
        model.addAttribute("isAdmin", roles.contains("ADMIN"));

        // Token expiry info (from authorized client)
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
            provider, authToken.getName());

        if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
            Instant expiresAt = authorizedClient.getAccessToken().getExpiresAt();
            if (expiresAt != null) {
                long secondsLeft = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
                model.addAttribute("tokenExpiresAt",   FMT.format(expiresAt));
                model.addAttribute("tokenSecondsLeft", secondsLeft);
                model.addAttribute("tokenExpired",     secondsLeft <= 0);
            }
        }

        boolean hasRefreshToken = authorizedClient != null
            && authorizedClient.getRefreshToken() != null;
        model.addAttribute("hasRefreshToken", hasRefreshToken);

        return "dashboard";
    }

    /**
     * Admin-only page.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin(@AuthenticationPrincipal OAuth2User user, Model model) {
        String name = (user instanceof OidcUser o) ? o.getFullName()
            : getAttrStr(user, "name", "login");
        String email = (user instanceof OidcUser o) ? o.getEmail()
            : getAttrStr(user, "email", "login");
        model.addAttribute("name", name);
        model.addAttribute("email", email);
        return "admin";
    }

    /**
     * User-only profile page.
     */
    @GetMapping("/user/profile")
    @PreAuthorize("hasRole('USER')")
    public String userProfile(@AuthenticationPrincipal OAuth2User user, Model model) {
        String name = (user instanceof OidcUser o) ? o.getFullName()
            : getAttrStr(user, "name", "login");
        String email = (user instanceof OidcUser o) ? o.getEmail()
            : getAttrStr(user, "email", "login");
        model.addAttribute("name", name);
        model.addAttribute("email", email);
        return "user-profile";
    }

    /**
     * Access denied page.
     */
    @GetMapping("/access-denied")
    public String accessDenied(@AuthenticationPrincipal OAuth2User user, Model model) {
        if (user != null) {
            String name = (user instanceof OidcUser o) ? o.getFullName()
                : getAttrStr(user, "name", "login");
            model.addAttribute("name", name);
        }
        return "access-denied";
    }

    /**
     * REST endpoint — returns user info + roles + provider as JSON.
     */
    @GetMapping("/api/me")
    @ResponseBody
    public Map<String, Object> me(@AuthenticationPrincipal OAuth2User user) {
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken)
            SecurityContextHolder.getContext().getAuthentication();

        List<String> roles = authToken.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .toList();

        String name  = (user instanceof OidcUser o) ? o.getFullName()
            : getAttrStr(user, "name", "login");
        String email = (user instanceof OidcUser o) ? o.getEmail()
            : getAttrStr(user, "email", "login");

        return Map.of(
            "provider", authToken.getAuthorizedClientRegistrationId(),
            "name",     name != null ? name : "",
            "email",    email != null ? email : "",
            "roles",    roles,
            "attributes", user.getAttributes()
        );
    }

    // Helper — returns first non-null attribute value from a list of keys
    private String getAttrStr(OAuth2User user, String... keys) {
        for (String key : keys) {
            Object val = user.getAttribute(key);
            if (val != null) return val.toString();
        }
        return null;
    }
}
