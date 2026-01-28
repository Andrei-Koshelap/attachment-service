package com.digivikings.repository;

import com.digivikings.domain.AttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<AttachmentEntity, UUID> {
    Optional<AttachmentEntity> findByIdAndOwnerId(UUID id, String ownerId);
}
