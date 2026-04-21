package com.frogpants.communitybackend.model;

import java.time.Instant;

public record TaskDataEntry(
        String playerId,
        String userName,
        String name,
        String taskName,
        boolean completed,
        Instant createdAt,
        Instant updatedAt
) {
}
