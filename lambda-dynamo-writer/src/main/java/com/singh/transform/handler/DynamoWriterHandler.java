package com.singh.transform.handler;

import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.singh.transform.dto.UserMigrationRecord;
import io.awspring.cloud.dynamodb.DynamoDbTableNameResolver;
import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Component
public class DynamoWriterHandler implements Function<SNSEvent, String> {

    private final DynamoDbTemplate dynamoDbTemplate;
    private final ObjectMapper objectMapper;
    private final DynamoDbTableNameResolver tableNameResolver;
    private static final TypeReference<List<UserMigrationRecord>> USER_MIGRATION_RECORD_LIST =
            new TypeReference<>() {};

    @Override
    public String apply(SNSEvent event) {

        if (event == null || CollectionUtils.isEmpty(event.getRecords())) {
            log.warn("Received SNS event with no records. Nothing to persist.");
            return "No SNS records to process";
        }
        String tableName = tableNameResolver.resolve(UserMigrationRecord.class);
        log.info("Resolved DynamoDB table name for UserMigrationRecord = {}", tableName);

        int successCount = 0;
        int failureCount = 0;

        for (SNSRecord snsRecord : event.getRecords()) {
            List<UserMigrationRecord> records;
            try {
                records = parseRecords(snsRecord.getSNS().getMessage());
            } catch (IllegalArgumentException e) {
                failureCount++;
                log.error("Failed to parse SNS record message. Skipping this record. Error: {}", e.getMessage(), e);
                continue;
            }
            
            for (UserMigrationRecord record : records) {
                if (record == null) {
                    failureCount++;
                    log.warn("Encountered null UserMigrationRecord in SNS payload. Skipping.");
                    continue;
                }
                try {
                    dynamoDbTemplate.save(record);
                    successCount++;
                } catch (Exception ex) {
                    failureCount++;
                    log.error("Failed to insert record with id: {}", record.getId(), ex);
                }
            }
        }

        log.info("Finished writing records to DynamoDB. Successes={}, Failures={}", successCount, failureCount);
        return String.format("Written %d records to DynamoDB (%d failures)", successCount, failureCount);
    }

    private List<UserMigrationRecord> parseRecords(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            throw new IllegalArgumentException("SNS message payload is empty");
        }
        try {
            return objectMapper.readValue(jsonPayload, USER_MIGRATION_RECORD_LIST);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to parse SNS JSON payload", e);
        }
    }
}
