package com.singh.dispatcher.dto;

import lombok.Getter;
import software.amazon.awssdk.regions.Region;

@Getter
public enum Location {
    REGION(Region.EU_WEST_1);
    private final Region region;

    Location(Region region) {
        this.region = region;
    }
}
