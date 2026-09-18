package com.example.sso.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SessionController — lightweight REST endpoint used by the client-side
 * session timeout countdown timer.
 *
 * GET /api/session-status returns:
 *   { "secondsRemaining": 1234, "active": true }   — session alive
 *   { "secondsRemaining": 0,    "active": false }   — session expired / not authenticated
 */
@RestController
public class SessionController {

    @GetMapping("/api/session-status")
    public ResponseEntity<Map<String, Object>> sessionStatus(HttpServletRequest request) {
        HttpSession session = request.getSession(false);  // false = don't create new session
        boolean authenticated = SecurityContextHolder.getContext().getAuthentication() != null
            && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
            && !(SecurityContextHolder.getContext().getAuthentication()
                 instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);

        if (session == null || !authenticated) {
            return ResponseEntity.ok(Map.of("secondsRemaining", 0, "active", false));
        }

        long maxInactiveSeconds = session.getMaxInactiveInterval(); // set by server.servlet.session.timeout
        long lastAccessedMs     = session.getLastAccessedTime();
        long nowMs              = System.currentTimeMillis();
        long elapsedSeconds     = (nowMs - lastAccessedMs) / 1000;
        long secondsRemaining   = Math.max(0, maxInactiveSeconds - elapsedSeconds);

        return ResponseEntity.ok(Map.of(
            "secondsRemaining", secondsRemaining,
            "active",           secondsRemaining > 0
        ));
    }
}
