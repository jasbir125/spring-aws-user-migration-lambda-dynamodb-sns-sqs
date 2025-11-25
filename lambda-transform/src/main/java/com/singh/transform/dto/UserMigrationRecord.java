package com.singh.transform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class UserMigrationRecord {
    private String id;
    private String name;
    private String email;
}
