package com.example.sso.controller;

import com.example.sso.model.User;
import com.example.sso.service.AuditService;
import com.example.sso.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserService userService;
    private final AuditService auditService;

    @GetMapping
    public String listUsers(Model model) {
        List<User> users = userService.findAll();

        long totalUsers = users.size();
        long adminCount = users.stream().filter(u -> "ADMIN".equals(u.getRole())).count();
        long userCount  = users.stream().filter(u -> "USER".equals(u.getRole())).count();
        Map<String, Long> providerCounts = users.stream()
                .collect(Collectors.groupingBy(User::getProvider, Collectors.counting()));

        model.addAttribute("users", users);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("userCount", userCount);
        model.addAttribute("providerCounts", providerCounts);
        return "admin/users";
    }

    @GetMapping("/{id}")
    public String viewUser(@PathVariable Long id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        model.addAttribute("user", user);
        return "admin/user-detail";
    }

    @PostMapping("/{id}/role")
    public String updateRole(@PathVariable Long id,
                             @RequestParam String role,
                             @AuthenticationPrincipal OAuth2User actor,
                             HttpServletRequest request) {
        userService.findById(id).ifPresent(target -> {
            String oldRole = target.getRole();
            userService.updateRole(id, role);
            String actorEmail = resolveActorEmail(actor);
            String ip = getClientIp(request);
            auditService.recordRoleChange(target.getEmail(), oldRole, role, actorEmail, ip);
        });
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             @AuthenticationPrincipal OAuth2User actor,
                             HttpServletRequest request) {
        userService.findById(id).ifPresent(target -> {
            userService.deleteById(id);
            String actorEmail = resolveActorEmail(actor);
            auditService.recordUserDelete(target.getEmail(), actorEmail, getClientIp(request));
        });
        return "redirect:/admin/users";
    }

    private String resolveActorEmail(OAuth2User actor) {
        if (actor == null) return "unknown";
        Object email = actor.getAttribute("email");
        if (email != null) return email.toString();
        Object login = actor.getAttribute("login"); // GitHub fallback
        return login != null ? login + "@github.com" : "unknown";
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
