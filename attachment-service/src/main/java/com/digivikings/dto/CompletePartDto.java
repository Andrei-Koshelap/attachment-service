package com.digivikings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CompletePartDto(
        @Min(1) int partNumber,
        @NotBlank String eTag
) {}