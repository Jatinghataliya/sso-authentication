package com.example.sso.repository;

import com.example.sso.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** All events for a specific user email, newest first. */
    List<AuditEvent> findByUserEmailOrderByOccurredAtDesc(String userEmail);

    /** All events of a specific type, newest first. */
    List<AuditEvent> findByEventTypeOrderByOccurredAtDesc(String eventType);

    /** All events, newest first — used for the admin audit page. */
    List<AuditEvent> findAllByOrderByOccurredAtDesc();
}
