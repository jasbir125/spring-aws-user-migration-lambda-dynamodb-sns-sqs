package com.singh.dispatcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class S3IngestLambdaFunctionApplication {

    public static void main(String[] args) {
        SpringApplication.run(S3IngestLambdaFunctionApplication.class, args);
    }
}
