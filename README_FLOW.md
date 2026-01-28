┌─────────┐
│ Client  │
└────┬────┘
    │
    │ 1) POST /attachments/init
    │    (filename, size, contentType)
    │
    v
┌───────────────────────────────┐
│        BÜKSTACK RUUTER         │
│───────────────────────────────│
│ - Auth (JWT / TIM)             │
│ - Rate / flow limits           │
│ - Input allowlist              │
└────┬──────────────────────────┘
    │
    │ 2) REST call (trusted context)
    │
    v
┌────────────────────────────────────────┐
│   ATTACHMENT SERVICE (Spring Boot)     │
│────────────────────────────────────────│
│ - Validate size & purpose              │
│ - Create metadata (PENDING_UPLOAD)    │
│ - Generate presigned upload URLs       │
└────┬───────────────────────────────────┘
    │
    │ 3) multipart init + presign
    │
    v
┌───────────────────────┐
│  S3 OBJECT STORAGE    │
│  (private bucket)     │
└──────────┬────────────┘
            │
            │ 4) Direct multipart upload
            │    (chunks, parallel, retry)
            │
            │<-----------------------------┐
            │                              │
┌──────────v──────────┐                   │
│       Client        │-------------------┘
└──────────┬──────────┘
            │
            │ 5) POST /attachments/complete
            │    (uploadId, parts, checksum)
            │
            v
┌───────────────────────────────┐
│        BÜKSTACK RUUTER         │
└────┬──────────────────────────┘
    │
    │ 6) Complete upload request
    │
    v
┌────────────────────────────────────────┐
│   ATTACHMENT SERVICE (Spring Boot)     │
│────────────────────────────────────────│
│ - Complete multipart upload            │
│ - HeadObject / MIME sniff (Tika)       │
│ - Check checksum & real size           │
│ - Update status = UPLOADED             │
└────┬───────────────────────────────────┘
    │
    │ 7) Async AV scan trigger
    │
    v
┌──────────────────────────┐
│    AV SCANNER SERVICE    │
│──────────────────────────│
│ - Stream file from S3    │
│ - Virus scan (ClamAV)    │
└────┬─────────────────────┘
    │
    │ 8) Scan result
    │    CLEAN / INFECTED
    │
    v
┌───────────────────────────────┐
│  METADATA DB (Resql / PG)     │
│───────────────────────────────│
│ - Store final status          │
└──────────┬────────────────────┘
            │
            │ 9) GET /attachments/{id}/download
            │
            v
┌───────────────────────────────┐
│        BÜKSTACK RUUTER         │
└────┬──────────────────────────┘
    │
    │ 10) Check access & status
    │
    v
┌────────────────────────────────────────┐
│   ATTACHMENT SERVICE (Spring Boot)     │
│────────────────────────────────────────│
│ - Allow download only if CLEAN         │
│ - Generate signed download URL         │
└────┬───────────────────────────────────┘
    │
    │ 11) Signed GET URL (short TTL)
    │
    v
┌───────────────────────┐
│  S3 OBJECT STORAGE    │
│  (direct download)   │
└──────────┬────────────┘
            │
            │ 12) File bytes
            │
            v
        ┌─────────┐
        │ Client  │
        └─────────┘
