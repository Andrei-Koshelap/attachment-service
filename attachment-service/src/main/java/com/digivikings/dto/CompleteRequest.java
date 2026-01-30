package com.digivikings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CompleteRequest(
        @NotBlank
        String attachmentId,
        @NotNull
        List<CompletePartDto> parts,
        @Pattern(regexp = "^[a-fA-F0-9]{64}$")
        String checksumSha256
) {}