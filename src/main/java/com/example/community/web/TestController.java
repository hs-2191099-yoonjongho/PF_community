package com.example.community.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/ping")
    public String pingUser() {
        return "pong-user";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/ping-admin")
    public String pingAdmin() {
        return "pong-admin";
    }
}