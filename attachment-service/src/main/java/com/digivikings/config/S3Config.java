package com.digivikings.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Bean
    public StaticCredentialsProvider awsCredentialsProvider(
            @Value("${app.s3.access-key}") String accessKey,
            @Value("${app.s3.secret-key}") String secretKey
    ) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Bean
    public S3Client s3Client(
            StaticCredentialsProvider creds,
            @Value("${app.s3.region:us-east-1}") String region,
            @Value("${app.s3.endpoint}") String endpoint
    ) {
        return S3Client.builder()
                .credentialsProvider(creds)
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint)) // MinIO/Ceph
                .forcePathStyle(true)                   //  MinIO
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(
            StaticCredentialsProvider creds,
            @Value("${app.s3.region:us-east-1}") String region,
            @Value("${app.s3.endpoint}") String endpoint
    ) {
        return S3Presigner.builder()
                .credentialsProvider(creds)
                .region(Region.of(region))
                .endpointOverride(URI.create(endpoint))
                .build();
    }
}
