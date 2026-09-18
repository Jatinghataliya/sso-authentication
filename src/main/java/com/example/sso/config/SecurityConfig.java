package com.example.sso.config;

import com.example.sso.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Set;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.client.provider.okta.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.client.registration.okta.client-id}")
    private String clientId;

    @Value("${app.base-url}")
    private String appBaseUrl;

    private final UserService userService;

    public SecurityConfig(@Lazy UserService userService) {
        this.userService = userService;
    }

    /**
     * OAuth2AuthorizedClientManager with refresh_token support.
     * This is the core bean that TokenRefreshFilter uses to silently renew tokens.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        // Support authorization_code (initial login) + refresh_token (silent renewal)
        OAuth2AuthorizedClientProvider authorizedClientProvider =
            OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build();

        DefaultOAuth2AuthorizedClientManager manager =
            new DefaultOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientRepository);
        manager.setAuthorizedClientProvider(authorizedClientProvider);
        return manager;
    }

    /**
     * TokenRefreshFilter bean — wired with the authorized client manager.
     */
    @Bean
    public TokenRefreshFilter tokenRefreshFilter(OAuth2AuthorizedClientManager authorizedClientManager) {
        return new TokenRefreshFilter(authorizedClientManager);
    }

    /**
     * OAuth2 login success handler — saves/updates user in DB, then redirects to /dashboard.
     */
    @Bean
    public AuthenticationSuccessHandler oAuth2LoginSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            if (authentication instanceof OAuth2AuthenticationToken token) {
                userService.upsertUser(token);
            }
            response.sendRedirect(request.getContextPath() + "/dashboard");
        };
    }

    /**
     * Auth0-specific logout handler.
     * Builds: https://<domain>/v2/logout?client_id=...&returnTo=http://localhost:8081/
     */
    @Bean
    public LogoutSuccessHandler oidcLogoutSuccessHandler() {
        return new LogoutSuccessHandler() {
            @Override
            public void onLogoutSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

                // Determine which provider was used
                String provider = "";
                if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
                    provider = oauthToken.getAuthorizedClientRegistrationId();
                }

                if ("okta".equals(provider)) {
                    // Auth0: redirect to /v2/logout so Auth0 also clears its session
                    String returnTo = appBaseUrl + "/";
                    String baseUrl = issuerUri.endsWith("/")
                        ? issuerUri.substring(0, issuerUri.length() - 1)
                        : issuerUri;
                    String logoutUrl = UriComponentsBuilder
                        .fromHttpUrl(baseUrl + "/v2/logout")
                        .queryParam("client_id", clientId)
                        .queryParam("returnTo", returnTo)
                        .toUriString();
                    response.sendRedirect(logoutUrl);
                } else {
                    // GitHub / Google — local session already cleared by Spring Security,
                    // just redirect back to home
                    response.sendRedirect(appBaseUrl + "/");
                }
            }
        };
    }

    /**
     * OIDC user service — only for Auth0. Enriches user with roles from JWT claims.
     * GitHub is plain OAuth2 and must NOT use this service.
     */
    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser oidcUser = delegate.loadUser(request);
            Set<GrantedAuthority> authorities = Auth0RolesExtractor.extractAuthorities(oidcUser.getAuthorities());
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), oidcUser.getUserInfo());
        };
    }

    /**
     * OAuth2 user service — for GitHub (plain OAuth2, no OIDC).
     * Uses Spring's default DefaultOAuth2UserService.
     */
    @Bean
    public OAuth2UserService<org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest,
                             org.springframework.security.oauth2.core.user.OAuth2User> oauth2UserService() {
        return new org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TokenRefreshFilter tokenRefreshFilter) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Publicly accessible pages
                .requestMatchers("/", "/public/**", "/css/**", "/js/**",
                    "/access-denied", "/login",
                    "/oauth2/authorization/**",           // OAuth2 initiation — must be public
                    "/h2-console/**",                     // H2 web console (dev only)
                    "/actuator/health",                   // Railway health probe — must be public
                    "/actuator/info",                     // App info — public
                    "/api/session-status"                 // Session countdown — must be public (returns 0 when expired)
                ).permitAll()
                // Role-protected pages
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasRole("USER")
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            .oauth2Login(oauth2 -> oauth2
                // No loginPage() — authenticationEntryPoint below handles redirect to /
                .successHandler(oAuth2LoginSuccessHandler())
                .failureUrl("/?error=true")
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(oidcUserService())   // Auth0 — OIDC path
                    .userService(oauth2UserService())      // GitHub — plain OAuth2 path
                )
            )

            .exceptionHandling(ex -> ex
                // Unauthenticated → redirect to our home page (not Spring's /login)
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendRedirect(request.getContextPath() + "/"))
                .accessDeniedPage("/access-denied")
            )

            .sessionManagement(session -> session
                // Redirect to home with ?expired=true when session times out
                .invalidSessionUrl("/?expired=true")
                // Only one session per user at a time
                .maximumSessions(1)
                .expiredUrl("/?expired=true")
            )

            .logout(logout -> logout
                .logoutRequestMatcher(new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/logout"))
                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            )

            // Allow H2 console iframes (same-origin)
            .headers(h -> h.frameOptions(f -> f.sameOrigin()))

            // Register the token refresh filter before the auth filter
            .addFilterBefore(tokenRefreshFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
