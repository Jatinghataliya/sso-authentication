package com.example.sso.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One of: LOGIN, LOGOUT, ROLE_CHANGE, USER_DELETE */
    @Column(nullable = false)
    private String eventType;

    /** Email of the user the event is about */
    @Column(nullable = false)
    private String userEmail;

    /** OAuth2 provider used (okta / github / google) */
    private String provider;

    /**
     * Extra context: e.g. "ADMIN → USER", "deleted by admin@example.com"
     * Nullable — not all event types need details.
     */
    private String details;

    /** IP address of the request that triggered the event */
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void prePersist() {
        if (occurredAt == null) occurredAt = LocalDateTime.now();
    }
}
