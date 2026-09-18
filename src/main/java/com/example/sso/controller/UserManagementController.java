package com.example.sso.controller;

import com.example.sso.model.User;
import com.example.sso.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public String updateRole(@PathVariable Long id, @RequestParam String role) {
        userService.updateRole(id, role);
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id) {
        userService.deleteById(id);
        return "redirect:/admin/users";
    }
}
