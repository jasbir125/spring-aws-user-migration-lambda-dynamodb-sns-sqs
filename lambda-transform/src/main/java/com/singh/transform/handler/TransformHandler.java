package com.singh.transform.handler;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.singh.transform.dto.UserMigrationRecord;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.List;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Component
public class TransformHandler implements Function<SNSEvent, String> {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${SNS_TRANSFORM_TO_DYNAMO_TOPIC_ARN:}")
    private String topicArnFromEnv;

    @Value("${aws.sns.destination:}")
    private String topicArnFromProperties;

    private String targetTopicArn;

    @PostConstruct
    void resolveTopicArn() {
        if (StringUtils.hasText(topicArnFromEnv)) {
            targetTopicArn = topicArnFromEnv;
        } else if (StringUtils.hasText(topicArnFromProperties)) {
            targetTopicArn = topicArnFromProperties;
        }

        if (!StringUtils.hasText(targetTopicArn)) {
            throw new IllegalStateException("SNS topic ARN not configured. " +
                    "Set SNS_TRANSFORM_TO_DYNAMO_TOPIC_ARN environment variable or aws.sns.destination property.");
        }
    }

    @Override
    public String apply(SNSEvent event) {

        String csv = event.getRecords().get(0).getSNS().getMessage();
        log.info("CSV Payload:\n{}", csv);

        List<UserMigrationRecord> records = parseCsv(csv);

        // transformation
        records.forEach(r -> r.setEmail(r.getEmail().toLowerCase()));

        // Convert to JSON for SNS publish
        try {
            String json = objectMapper.writeValueAsString(records);

            snsClient.publish(PublishRequest.builder().topicArn(targetTopicArn).message(json).build());

            log.info("Published transformed records to SNS topic {}", targetTopicArn);
            return "Published transformed records to SNS topic " + targetTopicArn;

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize records", e);
        }
    }

    private List<UserMigrationRecord> parseCsv(String csv) {
        return csv.lines().skip(1).map(line -> {
            String[] parts = line.split(",");
            return new UserMigrationRecord(parts[0], parts[1], parts[2]);
        }).toList();
    }
}