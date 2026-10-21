package com.devhub.organization;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("organizations")
public record Organization(
        @Id String id,
        String name,
        String ownerUserId,
        String productManagerAccessCodeHash,
        String seniorManagerAccessCodeHash,
        String developerAccessCodeHash,
        Instant createdAt,
        Instant updatedAt
) { }
