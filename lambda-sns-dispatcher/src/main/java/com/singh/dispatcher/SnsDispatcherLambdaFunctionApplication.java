package com.singh.dispatcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class SnsDispatcherLambdaFunctionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnsDispatcherLambdaFunctionApplication.class, args);
    }
}
