package com.digivikings.dto;

import java.util.Map;

public record InitResponse(
        String attachmentId,
        String bucket,
        String objectKey,
        String uploadId,
        Map<Integer, String> partUrls,
        long expiresInSeconds
) {}






