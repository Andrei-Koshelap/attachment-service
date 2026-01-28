package com.digivikings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record InitRequest(
        @NotBlank String filename,
        @NotBlank String contentType,
        @Min(1) @Max(104857600) long size // 100MB or more
) {}