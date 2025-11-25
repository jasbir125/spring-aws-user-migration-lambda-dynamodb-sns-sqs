package com.singh.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service
public class S3UploadService {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private final S3Client s3Client;

    public S3UploadService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String uploadCsv(MultipartFile file) throws IOException {
        checkIfFileIsEmpty(file);
        String key = buildObjectKey(file.getOriginalFilename());
        log.info("Uploading file {} ({} bytes) to bucket {}", file.getOriginalFilename(), file.getSize(), bucketName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("text/csv")
                .build();

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, file.getSize()));
            log.info("Successfully uploaded {} to {}", key, bucketName);
            return key;
        } catch (SdkException e) {
            log.error("AWS SDK error while uploading {} to {}", key, bucketName, e);
            throw new IllegalStateException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    private void checkIfFileIsEmpty(MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Attempted to upload empty file '{}'", file.getOriginalFilename());
            throw new IllegalArgumentException("Cannot upload empty file.");
        }
    }

    private String buildObjectKey(String originalFilename) {
        String sanitizedName = Optional.ofNullable(originalFilename).filter(StringUtils::hasText).orElse("upload.csv");
        return "users/" + sanitizedName;
    }
}
