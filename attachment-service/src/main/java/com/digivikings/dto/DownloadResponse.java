package com.digivikings.dto;

public record DownloadResponse(
        String url,
        long expiresInSeconds
) {}