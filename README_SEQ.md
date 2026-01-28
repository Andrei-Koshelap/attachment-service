Client
|
| POST /attachments/init
v
Ruuter (DSL)
|  auth + rate-limit + allowlist
|------------------------------------>
|                Attachment Service
|                  | create metadata (PENDING_UPLOAD)
|                  |------------------------>
|                  |         Metadata DB
|                  |
|                  | S3 multipart init + presign part URLs
|                  |------------------------>
|                  |         S3 Storage
|<------------------------------------|
|
|<-- {uploadId, partUrls} -------------
|
|  PUT parts directly (presigned URLs)
|----------------------------------------------> S3 Storage
|<---------------------------------------------- ETags
|
| POST /attachments/complete (parts+checksum)
v
Ruuter (DSL)
|------------------------------------>
|                Attachment Service
|                  | complete multipart
|                  |------------------------> S3 Storage
|                  | head/get range + MIME sniff
|                  |------------------------> S3 Storage
|                  | update status UPLOADED
|                  |------------------------> Metadata DB
|                  | trigger scan
|                  |------------------------> AV Scanner
|                                              | stream object
|                                              |-----------> S3 Storage
|                                              | scan (ClamAV)
|                                              |-----------> ClamAV
|                                              | update status CLEAN/INFECTED
|                                              |-----------> Metadata DB
|
| GET /attachments/{id}/download
v
Ruuter (DSL)
|------------------------------------>
|                Attachment Service
|                  | check status == CLEAN
|                  |------------------------> Metadata DB
|                  | presign GET (short TTL)
|                  |------------------------> S3 Storage
|<------------------------------------|
|
| Client downloads directly signed URL
|----------------------------------------------> S3 Storage
|<---------------------------------------------- bytes
