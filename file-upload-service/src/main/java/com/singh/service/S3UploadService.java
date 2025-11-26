package com.singh.service;

import io.awspring.cloud.sns.core.SnsTemplate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final S3Client s3Client;
    private final SnsTemplate snsTemplate;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.keyPath}")
    private String keyPath;

    @Value("${SNS_INGEST_TO_TRANSFORM_TOPIC_ARN:}")
    private String topicArnFromEnv;

    @Value("${aws.sns.destination:}")
    private String topicArnFromProperties;

    private String targetTopicArn;

    @PostConstruct
    void validateTopicArn() {
        this.targetTopicArn =
                StringUtils.hasText(topicArnFromEnv)
                        ? topicArnFromEnv
                        : topicArnFromProperties;

        if (!StringUtils.hasText(targetTopicArn)) {
            throw new IllegalStateException("""
                    SNS topic ARN is not configured.
                    Set SNS_INGEST_TO_TRANSFORM_TOPIC_ARN (environment variable)
                    or aws.sns.destination (application.properties).
                    """);
        }

        log.info("SNS Topic ARN configured for ingestion: {}", targetTopicArn);
    }

    /**
     * Uploads CSV/JSON file to S3 and optionally notifies SNS
     */
    public String uploadCsv(MultipartFile file) throws IOException {
        validateNotEmpty(file);

        String key = generateObjectKey(file.getOriginalFilename());
        log.info("Uploading '{}' ({} bytes) -> s3://{}{}/{}",
                file.getOriginalFilename(), file.getSize(), bucketName, keyPath, key);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(keyPath)
                .key(key)
                .contentType(file.getContentType() != null ? file.getContentType() : "text/csv")
                .build();

        try (InputStream inputStream = file.getInputStream()) {

            // upload file
            s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, file.getSize()));

            log.info("Uploaded successfully: s3://{}{}/{}", bucketName, keyPath, key);
            return key;

        } catch (Exception e) {
            log.error("Upload failed for s3://{}/{}", bucketName, key, e);
            throw new IllegalStateException("S3 upload failed: " + e.getMessage(), e);
        }
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty or missing");
        }
    }

    private String generateObjectKey(String originalFilename) {
        String fileName = Optional.ofNullable(originalFilename)
                .filter(StringUtils::hasText)
                .orElse("upload.csv");

        return "/" + fileName;
    }
}