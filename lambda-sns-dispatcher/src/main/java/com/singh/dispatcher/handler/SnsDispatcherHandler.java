package com.singh.dispatcher.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;

import java.util.function.Function;

@Slf4j
@Component
public class SnsDispatcherHandler implements Function<DynamodbEvent, String> {

    private static final String FANOUT_TOPIC_ARN = System.getenv("SNS_USER_MIGRATION_FANOUT_TOPIC_ARN");

    @Override
    public String apply(DynamodbEvent event) {

        log.info("FANOUT_TOPIC_ARN :{}",FANOUT_TOPIC_ARN);
        log.info("Received {} DynamoDB stream records", event.getRecords().size());

        event.getRecords().forEach(record -> {
            log.info("Event Name: {}", record.getEventName());
            log.info("Keys: {}", record.getDynamodb().getKeys());
        });


        return "Processed " + event.getRecords().size() + " DynamoDB stream events";
    }
}