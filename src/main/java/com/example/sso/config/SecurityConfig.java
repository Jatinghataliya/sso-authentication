package com.example.sso.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Route authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/public/**", "/css/**", "/js/**").permitAll()
                .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN")
                .anyRequest().authenticated()
            )

            // Enable OAuth2 / OIDC login via Okta
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/oauth2/authorization/okta")        // Custom login trigger URL
                .defaultSuccessUrl("/dashboard", true)          // Redirect after successful login
                .failureUrl("/login?error=true")
            )

            // Logout configuration
            .logout(logout -> logout
                .logoutSuccessUrl("/")                          // Redirect after logout
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }
}
