package com.digivikings.service;


import com.digivikings.domain.AttachmentEntity;
import com.digivikings.domain.AttachmentStatus;
import com.digivikings.repository.AttachmentRepository;
import fi.solita.clamav.ClamAVClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

@Service
public class VirusScanService {

    private final AttachmentRepository repo;
    private final S3Client s3;
    private final ClamAVClient clam;

    public VirusScanService(
            AttachmentRepository repo,
            S3Client s3,
            @Value("${app.clamav.host:localhost}") String clamHost,
            @Value("${app.clamav.port:3310}") int clamPort
    ) {
        this.repo = repo;
        this.s3 = s3;
        this.clam = new ClamAVClient(clamHost, clamPort);
    }

    public void scanNow(UUID attachmentId) {
        AttachmentEntity a = repo.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));

        if (a.getStatus() != AttachmentStatus.UPLOADED) return;

        try (ResponseInputStream<?> s3Stream = s3.getObject(
                GetObjectRequest.builder()
                        .bucket(a.getBucket())
                        .key(a.getObjectKey())
                        .build()
        )) {
            byte[] reply = clam.scan((InputStream) s3Stream);
            boolean infected = !ClamAVClient.isCleanReply(reply);

            a.setScannedAt(Instant.now());
            a.setStatus(infected ? AttachmentStatus.INFECTED : AttachmentStatus.CLEAN);
            if (infected) a.setRejectReason("VIRUS_FOUND");
            repo.save(a);

        } catch (Exception e) {
            a.setScannedAt(Instant.now());
            a.setStatus(AttachmentStatus.SCAN_FAILED);
            a.setRejectReason("SCAN_ERROR");
            repo.save(a);
        }
    }
}
