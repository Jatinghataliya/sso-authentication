package com.example.sso.service;

import com.example.sso.model.AuditEvent;
import com.example.sso.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public void recordLogin(String email, String provider, String ipAddress) {
        save(AuditEvent.builder()
                .eventType("LOGIN")
                .userEmail(email)
                .provider(provider)
                .ipAddress(ipAddress)
                .build());
    }

    public void recordLogout(String email, String provider, String ipAddress) {
        save(AuditEvent.builder()
                .eventType("LOGOUT")
                .userEmail(email)
                .provider(provider)
                .ipAddress(ipAddress)
                .build());
    }

    public void recordRoleChange(String targetEmail, String oldRole, String newRole,
                                  String actorEmail, String ipAddress) {
        save(AuditEvent.builder()
                .eventType("ROLE_CHANGE")
                .userEmail(targetEmail)
                .details(oldRole + " → " + newRole + " (by " + actorEmail + ")")
                .ipAddress(ipAddress)
                .build());
    }

    public void recordUserDelete(String targetEmail, String actorEmail, String ipAddress) {
        save(AuditEvent.builder()
                .eventType("USER_DELETE")
                .userEmail(targetEmail)
                .details("deleted by " + actorEmail)
                .ipAddress(ipAddress)
                .build());
    }

    public List<AuditEvent> findAll() {
        return auditEventRepository.findAllByOrderByOccurredAtDesc();
    }

    public List<AuditEvent> findByUser(String email) {
        return auditEventRepository.findByUserEmailOrderByOccurredAtDesc(email);
    }

    private void save(AuditEvent event) {
        auditEventRepository.save(event);
    }
}
