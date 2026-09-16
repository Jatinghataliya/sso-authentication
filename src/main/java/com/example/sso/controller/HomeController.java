package com.example.sso.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /**
     * Public home page — accessible without login.
     */
    @GetMapping("/")
    public String home() {
        return "home";
    }

    /**
     * Triggers the Okta OIDC login flow.
     * Spring Security intercepts /oauth2/authorization/okta automatically,
     * so this method is only here for completeness / explicit mapping.
     */
    @GetMapping("/login")
    public String login() {
        return "redirect:/oauth2/authorization/okta";
    }
}
