package com.singh.dispatcher.handler;

import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.singh.dispatcher.dto.S3ClientHelper;
import io.awspring.cloud.sns.core.SnsTemplate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3IngestHandler implements Function<S3EventNotification, String> {

    private final SnsTemplate snsTemplate;

    @Value("${SNS_INGEST_TO_TRANSFORM_TOPIC_ARN:}")
    private String topicArnFromEnv;

    @Value("${aws.sns.destination:}")
    private String topicArnFromProperties;

    private String targetTopicArn;

    @PostConstruct
    void validateTargetTopic() {
        targetTopicArn = StringUtils.hasText(topicArnFromEnv) ? topicArnFromEnv : topicArnFromProperties;

        if (!StringUtils.hasText(targetTopicArn)) {
            throw new IllegalStateException("""
                    SNS topic ARN missing:
                    ➤ Set environment variable: SNS_INGEST_TO_TRANSFORM_TOPIC_ARN
                    ➤ Or configure application property: aws.sns.destination
                    """);
        }

        log.info("Configured ingest SNS topic ARN: {}", targetTopicArn);
    }

    @Override
    public String apply(S3EventNotification event) {

        if (event == null || CollectionUtils.isEmpty(event.getRecords())) {
            log.warn("S3 event contained no records. Nothing to process.");
            return "No S3 records to process";
        }

        // Initialize S3 client
        S3Client s3;
        try {
            s3 = S3ClientHelper.getS3Client();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create S3 client", e);
        }

        // Extract bucket + key
        var record = Optional.ofNullable(event.getRecords().get(0))
                .map(S3EventNotification.S3EventNotificationRecord::getS3)
                .orElseThrow(() -> new IllegalArgumentException("Missing S3 details in event."));

        String bucket = record.getBucket().getName();
        String key = record.getObject().getKey();

        log.info("Incoming S3 event → bucket='{}', key='{}'", bucket, key);

        // 🔥 1) Skip folder placeholders (keys ending with "/")
        if (key.endsWith("/")) {
            log.warn("Skipping folder placeholder key: {}", key);
            return "Skipped folder placeholder";
        }

        // 🔥 2) Check object metadata (size, content-type)
        HeadObjectResponse head;
        try {
            head = s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.error("Unable to HEAD S3 object {}/{}", bucket, key, e);
            return "Failed to HEAD object";
        }

        long size = head.contentLength();
        log.info("S3 object metadata → size={} bytes, content-type={}", size, head.contentType());

        // 🔥 3) Skip zero-byte objects
        if (size == 0) {
            log.warn("Skipping zero-length object: {}/{}", bucket, key);
            return "Skipped empty object";
        }

        // 🔥 4) Read file content safely
        try (InputStream inputStream = s3.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build()
        )) {

            String fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            if (!StringUtils.hasText(fileContent)) {
                log.warn("Object {} has blank content. Skipping publish.", key);
                return "Blank content skipped";
            }

            log.info("S3 file content received ({} chars)", fileContent.length());

            // 5) Publish content to SNS
            snsTemplate.convertAndSend(
                    Objects.requireNonNull(targetTopicArn),
                    fileContent
            );

            log.info("Published S3 object {} to SNS topic {}", key, targetTopicArn);
            return "Successfully published S3 file to SNS";

        } catch (Exception e) {
            log.error("Error while reading or publishing S3 file {}/{}", bucket, key, e);
            return "Error processing S3 object (consumed)";
        }
    }
}