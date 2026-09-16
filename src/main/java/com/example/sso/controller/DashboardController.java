package com.example.sso.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class DashboardController {

    /**
     * Protected dashboard — requires the user to be authenticated via Okta SSO.
     * OidcUser is populated automatically by Spring Security after a successful login.
     */
    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("name",      user.getFullName());
        model.addAttribute("email",     user.getEmail());
        model.addAttribute("subject",   user.getSubject());         // Okta unique user ID
        model.addAttribute("locale",    user.getUserInfo().getLocale());
        model.addAttribute("claims",    user.getClaims());          // All JWT claims
        return "dashboard";
    }

    /**
     * REST endpoint — returns the authenticated user's token claims as JSON.
     * Useful for debugging or downstream API calls.
     */
    @GetMapping("/api/me")
    @ResponseBody
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
        return Map.of(
            "subject",  user.getSubject(),
            "name",     user.getFullName(),
            "email",    user.getEmail(),
            "claims",   user.getClaims()
        );
    }

    /**
     * Admin-only page. Access is restricted in SecurityConfig via hasAuthority("ROLE_ADMIN").
     */
    @GetMapping("/admin")
    public String admin(@AuthenticationPrincipal OidcUser user, Model model) {
        model.addAttribute("name", user.getFullName());
        return "admin";
    }
}
