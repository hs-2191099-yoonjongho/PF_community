package com.example.community.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record SignUp(
            @NotBlank @Size(min = 3, max = 30) String username,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password
    ) {}
    public record Login(
            @NotBlank String username,
            @NotBlank String password
    ) {}
}
