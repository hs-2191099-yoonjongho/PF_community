package com.example.community.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PostDtos {
    public record Create(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content
    ) {}
    public record Update(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content
    ) {}
}
