package com.example.sso.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
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
                String returnTo = "http://localhost:8081/";

                // Strip trailing slash from issuerUri to avoid double-slash
                String baseUrl = issuerUri.endsWith("/")
                    ? issuerUri.substring(0, issuerUri.length() - 1)
                    : issuerUri;

                String logoutUrl = UriComponentsBuilder
                    .fromHttpUrl(baseUrl + "/v2/logout")
                    .queryParam("client_id", clientId)
                    .queryParam("returnTo", returnTo)
                    .toUriString();

                response.sendRedirect(logoutUrl);
            }
        };
    }

    /**
     * Custom OidcUserService — enriches Auth0 user with roles from JWT claims.
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   TokenRefreshFilter tokenRefreshFilter) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/public/**", "/css/**", "/js/**", "/access-denied", "/login").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasRole("USER")
                .anyRequest().authenticated()
            )

            .oauth2Login(oauth2 -> oauth2
                // No loginPage() — Spring will auto-serve /login showing both providers
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(oidcUserService())   // Auth0 (OIDC)
                    // GitHub uses default DefaultOAuth2UserService automatically
                )
            )

            .exceptionHandling(ex -> ex
                .accessDeniedPage("/access-denied")
            )

            .logout(logout -> logout
                .logoutRequestMatcher(new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/logout"))  // allow GET /logout
                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            )

            // Register the token refresh filter before the auth filter
            .addFilterBefore(tokenRefreshFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
