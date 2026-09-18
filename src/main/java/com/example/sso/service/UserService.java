package com.example.sso.service;

import com.example.sso.model.User;
import com.example.sso.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User upsertUser(OAuth2AuthenticationToken token) {
        String provider = token.getAuthorizedClientRegistrationId();
        OAuth2User oauth2User = token.getPrincipal();
        Map<String, Object> attrs = oauth2User.getAttributes();

        // Extract providerUserId
        String providerUserId;
        if ("github".equals(provider)) {
            Object idObj = attrs.get("id");
            providerUserId = idObj != null ? idObj.toString() : "";
        } else {
            // Auth0 (okta) and Google use "sub"
            providerUserId = (String) attrs.getOrDefault("sub", "");
        }

        // Extract email — GitHub may return null
        String email;
        if ("github".equals(provider)) {
            Object emailObj = attrs.get("email");
            String login = (String) attrs.getOrDefault("login", "unknown");
            email = (emailObj != null && !emailObj.toString().isBlank())
                    ? emailObj.toString()
                    : login + "@github.com";
        } else {
            email = (String) attrs.getOrDefault("email", "");
        }

        // Extract name — GitHub may return null
        String name;
        if ("github".equals(provider)) {
            Object nameObj = attrs.get("name");
            String login = (String) attrs.getOrDefault("login", "unknown");
            name = (nameObj != null && !nameObj.toString().isBlank())
                    ? nameObj.toString()
                    : login;
        } else {
            name = (String) attrs.getOrDefault("name", email);
        }

        // Extract avatarUrl
        String avatarUrl;
        if ("github".equals(provider)) {
            avatarUrl = (String) attrs.get("avatar_url");
        } else {
            // Google and Auth0 both use "picture"
            avatarUrl = (String) attrs.get("picture");
        }

        // Upsert: look up by providerUserId
        Optional<User> existing = userRepository.findByProviderUserId(providerUserId);
        if (existing.isPresent()) {
            User user = existing.get();
            user.setLastLoginAt(LocalDateTime.now());
            user.setName(name);
            user.setAvatarUrl(avatarUrl);
            return userRepository.save(user);
        } else {
            User user = User.builder()
                    .email(email)
                    .name(name)
                    .provider(provider)
                    .providerUserId(providerUserId)
                    .role("USER")
                    .avatarUrl(avatarUrl)
                    .createdAt(LocalDateTime.now())
                    .lastLoginAt(LocalDateTime.now())
                    .build();
            return userRepository.save(user);
        }
    }

    public List<User> findAll() {
        return userRepository.findAllByOrderByLastLoginAtDesc();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public void updateRole(Long id, String role) {
        userRepository.findById(id).ifPresent(user -> {
            user.setRole(role);
            userRepository.save(user);
        });
    }

    @Transactional
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    public long getTotalUsers() {
        return userRepository.count();
    }

    public List<User> getRecentLogins(int limit) {
        return userRepository.findAllByOrderByLastLoginAtDesc()
                .stream()
                .limit(limit)
                .toList();
    }
}
