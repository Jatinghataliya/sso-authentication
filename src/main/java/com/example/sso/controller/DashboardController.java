package com.example.sso.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    /**
     * Protected dashboard — available to all authenticated users.
     * Passes roles to the template so UI can conditionally show sections.
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("name",    user.getFullName());
        model.addAttribute("email",   user.getEmail());
        model.addAttribute("subject", user.getSubject());
        model.addAttribute("claims",  user.getClaims());

        // Extract role names from Spring Security authorities (strip "ROLE_" prefix for display)
        List<String> roles = SecurityContextHolder.getContext()
            .getAuthentication().getAuthorities().stream()
            .map(a -> a.getAuthority())
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))           // "ROLE_ADMIN" → "ADMIN"
            .toList();

        model.addAttribute("roles", roles);
        model.addAttribute("isAdmin", roles.contains("ADMIN"));
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
