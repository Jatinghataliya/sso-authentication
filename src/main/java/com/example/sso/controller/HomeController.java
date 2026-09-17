package com.example.sso.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /**
     * Public home page — shows both login buttons (Auth0 + GitHub).
     */
    @GetMapping("/")
    public String home() {
        return "home";
    }

    /**
     * Spring Security redirects unauthenticated users to /login by default.
     * We forward them to / which shows our styled login page with both provider buttons.
     */
    @GetMapping("/login")
    public String login() {
        return "home";
    }
}
