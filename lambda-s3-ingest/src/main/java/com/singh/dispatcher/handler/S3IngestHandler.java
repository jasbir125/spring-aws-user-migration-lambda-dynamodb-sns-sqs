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
            throw new IllegalStateException("SNS topic ARN not configured. " +
                    "Set SNS_INGEST_TO_TRANSFORM_TOPIC_ARN environment variable or aws.sns.destination property.");
        }
        log.info("Configured ingest SNS topic ARN: {}", targetTopicArn);
    }

    @Override
    public String apply(S3EventNotification event) {

        if (event == null || CollectionUtils.isEmpty(event.getRecords())) {
            log.warn("S3 event contained no records. Nothing to process.");
            return "No S3 records to process";
        }

        S3Client s3;
        try {
            s3 = S3ClientHelper.getS3Client();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create S3 client", e);
        }

        var record = Optional.ofNullable(event.getRecords().get(0))
                .map(S3EventNotification.S3EventNotificationRecord::getS3)
                .orElseThrow(() -> new IllegalArgumentException("S3 event record is missing S3 details"));
        String bucket = record.getBucket().getName();
        String key = record.getObject().getKey();

        log.info("Processing S3 file: bucket={}, key={}", bucket, key);

        try (InputStream inputStream = s3.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        )) {

            String fileContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("File received from S3:\n{}", fileContent);

            snsTemplate.convertAndSend(Objects.requireNonNull(targetTopicArn), fileContent);

            log.info("Published S3 object {} to SNS topic {}", key, targetTopicArn);
            return "Successfully published CSV/JSON contents to SNS topic";
        } catch (Exception e) {
            log.error("Error while processing file from S3 bucket={}, key={}", bucket, key, e);
            throw new IllegalStateException("Failed to process S3 object " + key, e);
        }
    }
}