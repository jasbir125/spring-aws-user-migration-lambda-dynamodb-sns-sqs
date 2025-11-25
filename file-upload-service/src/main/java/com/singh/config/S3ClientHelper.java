package com.singh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

@Component
public class S3ClientHelper {

    @Value("${aws.s3.endpoint}")
    private String awsS3EndPoint;

    @Value("${aws.region}")
    protected String awsRegion;

    @Bean
    public  S3Client getS3Client() throws IOException {

        var clientBuilder = S3Client.builder();
        if (Objects.nonNull(awsS3EndPoint)) {
            return clientBuilder
                    .region(Region.of(awsRegion))
                    .endpointOverride(URI.create(awsS3EndPoint))
                    .forcePathStyle(true)
                    .build();
        } else {
            return clientBuilder.build();
        }
    }

}
