package com.digivikings.controller;

import com.digivikings.domain.AttachmentEntity;
import com.digivikings.dto.CompleteRequest;
import com.digivikings.dto.DownloadResponse;
import com.digivikings.dto.InitRequest;
import com.digivikings.dto.InitResponse;
import com.digivikings.service.AttachmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping("/init")
    public ResponseEntity<InitResponse> init(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InitRequest req
    ) {
        String ownerId = jwt.getSubject();

        AttachmentEntity a = service.init(ownerId, req.filename(), req.contentType(), req.size());

        // ~ part count = ceil(size/5MB), min 1, max 10000
        long partSize = 5L * 1024 * 1024;
        int partCount = (int) Math.max(1, Math.min(10_000, (req.size() + partSize - 1) / partSize));

        var partUrls = service.presignPartUrls(a, partCount);

        return ResponseEntity.ok(new InitResponse(
                a.getId().toString(),
                a.getBucket(),
                a.getObjectKey(),
                a.getUploadId(),
                partUrls,
                300
        ));
    }

    @PostMapping("/complete")
    public ResponseEntity<?> complete(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CompleteRequest req
    ) {
        String ownerId = jwt.getSubject();
        UUID id = UUID.fromString(req.attachmentId());

        List<CompletedPart> parts = req.parts().stream()
                .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build())
                .toList();

        service.complete(ownerId, id, parts, req.checksumSha256());
        return ResponseEntity.ok().body(java.util.Map.of("status", "SCAN_QUEUED"));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<DownloadResponse> download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") UUID id
    ) {
        String ownerId = jwt.getSubject();
        String url = service.presignDownload(ownerId, id);
        return ResponseEntity.ok(new DownloadResponse(url, 300));
    }
}
