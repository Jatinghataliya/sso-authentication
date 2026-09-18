package com.example.sso.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String name;

    @Column(nullable = false)
    private String provider;  // "okta", "github", "google"

    private String providerUserId;  // subject from OAuth2

    @Column(nullable = false)
    private String role;  // "USER" or "ADMIN"

    private String avatarUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;
}
