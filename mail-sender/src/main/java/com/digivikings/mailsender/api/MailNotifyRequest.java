package com.digivikings.mailsender.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MailNotifyRequest(
        @Email
        @NotBlank
        String recipient,
        @NotBlank
        String subject,
        @NotBlank
        String message
) {}
