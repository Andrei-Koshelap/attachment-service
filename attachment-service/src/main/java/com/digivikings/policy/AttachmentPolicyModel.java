package com.digivikings.policy;

import java.util.List;
import java.util.Set;

public record AttachmentPolicyModel(
        int maxFileSizeMb,
        Set<String> allowedMimeTypes,
        Presign presign,
        Limits limits,
        Behavior behavior
) {
    public record Presign(int uploadTtlSeconds, int downloadTtlSeconds) {}
    public record Limits(int maxUploadsPerUserPerMinute, int maxActiveUploadsPerUser) {}
    public record Behavior(boolean notifyOnClean, boolean notifyOnInfected) {}

    public static AttachmentPolicyModel defaults() {
        return new AttachmentPolicyModel(
                100,
                Set.of("application/pdf", "image/png", "image/jpeg"),
                new Presign(300, 180),
                new Limits(20, 5),
                new Behavior(true, true)
        );
    }

    public long maxFileSizeBytes() {
        return (long) maxFileSizeMb * 1024 * 1024;
    }
}
