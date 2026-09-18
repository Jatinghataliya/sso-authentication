package com.example.sso.controller;

import com.example.sso.model.AuditEvent;
import com.example.sso.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public String auditLog(@RequestParam(required = false) String filter,
                           @RequestParam(required = false) String user,
                           Model model) {

        List<AuditEvent> events;

        if (user != null && !user.isBlank()) {
            events = auditService.findByUser(user.trim());
            model.addAttribute("userFilter", user.trim());
        } else if (filter != null && !filter.isBlank()) {
            events = auditService.findAll().stream()
                    .filter(e -> e.getEventType().equalsIgnoreCase(filter.trim()))
                    .toList();
            model.addAttribute("typeFilter", filter.trim().toUpperCase());
        } else {
            events = auditService.findAll();
        }

        model.addAttribute("events", events);
        model.addAttribute("totalEvents", events.size());
        return "admin/audit";
    }
}
