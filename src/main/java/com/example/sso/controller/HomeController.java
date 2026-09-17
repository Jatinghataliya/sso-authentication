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
     * /login redirects to home page which has the styled provider buttons.
     * Spring Security's default /login page is replaced by our home.html.
     */
    @GetMapping("/login")
    public String login() {
        return "redirect:/";
    }
}
