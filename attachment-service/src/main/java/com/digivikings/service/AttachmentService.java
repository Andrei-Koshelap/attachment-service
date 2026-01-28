package com.digivikings.service;

import com.digivikings.domain.AttachmentEntity;
import com.digivikings.domain.AttachmentStatus;
import com.digivikings.repository.AttachmentRepository;
import com.digivikings.utils.FileSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.time.Duration;
import java.util.*;

@Service
public class AttachmentService {

    private final AttachmentRepository repo;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final VirusScanService virusScanService;

    private final String bucket;
    private final Duration presignTtl;

    public AttachmentService(
            AttachmentRepository repo,
            S3Client s3,
            S3Presigner presigner,
            VirusScanService virusScanService,
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.presign-ttl-seconds:300}") long ttlSeconds
    ) {
        this.repo = repo;
        this.s3 = s3;
        this.presigner = presigner;
        this.virusScanService = virusScanService;
        this.bucket = bucket;
        this.presignTtl = Duration.ofSeconds(ttlSeconds);
    }

    public AttachmentEntity init(String ownerId, String filename, String contentType, long size) {
        // business validating before signed URLs
        if (size <= 0 || size > 104_857_600L) throw new IllegalArgumentException("FILE_TOO_LARGE");
        String safeName = FileSecurity.sanitizeFilename(filename);

        AttachmentEntity a = new AttachmentEntity();
        a.setOwnerId(ownerId);
        a.setBucket(bucket);
        a.setObjectKey("attachments/" + UUID.randomUUID());
        a.setOriginalFilename(safeName);
        a.setDeclaredContentType(contentType);
        a.setDeclaredSize(size);
        a.setStatus(AttachmentStatus.PENDING_UPLOAD);

        repo.save(a);

        // init multipart
        CreateMultipartUploadResponse resp = s3.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(bucket)
                        .key(a.getObjectKey())
                        .contentType(contentType)
                        .build()
        );
        a.setUploadId(resp.uploadId());
        repo.save(a);

        return a;
    }

    public Map<Integer, String> presignPartUrls(AttachmentEntity a, int partCount) {
        Map<Integer, String> urls = new LinkedHashMap<>();
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            UploadPartRequest upr = UploadPartRequest.builder()
                    .bucket(a.getBucket())
                    .key(a.getObjectKey())
                    .uploadId(a.getUploadId())
                    .partNumber(partNumber)
                    .build();

            PresignedUploadPartRequest pre = presigner.presignUploadPart(
                    UploadPartPresignRequest.builder()
                            .signatureDuration(presignTtl)
                            .uploadPartRequest(upr)
                            .build()
            );
            urls.put(partNumber, pre.url().toString());
        }
        return urls;
    }

    public void complete(String ownerId, UUID id, List<CompletedPart> parts, String checksumSha256) {
        AttachmentEntity a = repo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NoSuchElementException("NOT_FOUND"));

        if (a.getStatus() != AttachmentStatus.PENDING_UPLOAD) {
            throw new IllegalStateException("INVALID_STATUS");
        }

        // complete multipart
        s3.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(a.getBucket())
                        .key(a.getObjectKey())
                        .uploadId(a.getUploadId())
                        .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                        .build()
        );

        // head bytes for MIME sniff (not believe user-provided Content-Type and H)
        byte[] head = s3.getObject(
                GetObjectRequest.builder().bucket(a.getBucket()).key(a.getObjectKey()).range("bytes=0-8191").build(),
                ResponseTransformer.toBytes()
        ).asByteArray();

        String detected = FileSecurity.sniffAndValidateMime(head);

        a.setDetectedContentType(detected);
        a.setSha256(checksumSha256);
        a.setStatus(AttachmentStatus.UPLOADED);
        repo.save(a);

        // launch of AV (better async)
        virusScanService.scanNow(a.getId());
    }

    public String presignDownload(String ownerId, UUID id) {
        AttachmentEntity a = repo.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NoSuchElementException("NOT_FOUND"));

        if (a.getStatus() != AttachmentStatus.CLEAN) {
            throw new IllegalStateException("NOT_READY_OR_BLOCKED");
        }

        // signed GET
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(a.getBucket())
                .key(a.getObjectKey())
                // .responseContentDisposition("attachment; filename=\"" + a.getOriginalFilename() + "\"")
                .build();

        PresignedGetObjectRequest pre = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(presignTtl)
                        .getObjectRequest(get)
                        .build()
        );
        return pre.url().toString();
    }
}
