package com.frogpants.communitybackend.model;

import java.time.Instant;

public record TaskDataEntry(
        String name,
        boolean completed,
        Instant createdAt,
        Instant updatedAt
) {
}
