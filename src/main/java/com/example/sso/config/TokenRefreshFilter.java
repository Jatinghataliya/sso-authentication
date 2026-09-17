package com.example.sso.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * TokenRefreshFilter — runs once per request.
 *
 * How it works:
 *  1. Checks if the current user has an OAuth2 session (OAuth2AuthenticationToken).
 *  2. Fetches the authorized client from the manager.
 *  3. If the access token is expired (or within the 60-second buffer), it calls
 *     OAuth2AuthorizedClientManager.authorize() which silently uses the refresh token
 *     to get a new access token from Auth0 — no redirect, no user interaction.
 *  4. Logs the token status on every request (visible in console during development).
 */
public class TokenRefreshFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshFilter.class);

    // Refresh proactively if token expires within this many seconds
    private static final int EXPIRY_BUFFER_SECONDS = 60;

    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public TokenRefreshFilter(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Only process authenticated OAuth2 users
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            String clientRegistrationId = oauthToken.getAuthorizedClientRegistrationId();

            try {
                // Ask the manager for the authorized client — it will refresh if needed
                OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId(clientRegistrationId)
                    .principal(authentication)
                    .attribute(HttpServletRequest.class.getName(), request)
                    .attribute(HttpServletResponse.class.getName(), response)
                    .build();

                OAuth2AuthorizedClient authorizedClient =
                    authorizedClientManager.authorize(authorizeRequest);

                if (authorizedClient != null) {
                    OAuth2AccessToken accessToken = authorizedClient.getAccessToken();

                    if (accessToken != null && accessToken.getExpiresAt() != null) {
                        Instant expiresAt = accessToken.getExpiresAt();
                        long secondsUntilExpiry = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();

                        if (secondsUntilExpiry <= EXPIRY_BUFFER_SECONDS) {
                            log.info("🔄 Access token refreshed silently for user: {} (was expiring in {}s)",
                                oauthToken.getName(), secondsUntilExpiry);
                        } else {
                            log.debug("✅ Access token valid for user: {} — expires in {}s",
                                oauthToken.getName(), secondsUntilExpiry);
                        }
                    }
                }

            } catch (Exception e) {
                // Refresh failed (e.g. refresh token expired) — log and continue
                // User will be redirected to login on the next protected request
                log.warn("⚠️ Token refresh failed for user: {} — {}", oauthToken.getName(), e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Skip static resources — no need to check token on CSS/JS/images.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/")
            || path.startsWith("/js/")
            || path.startsWith("/images/")
            || path.startsWith("/favicon");
    }
}
