package com.frogpants.communitybackend.model;

import jakarta.validation.constraints.Size;

public record TaskDataRequest(
        @Size(max = 120) String name,
        @Size(max = 120) String taskName,
        @Size(max = 120) String userName,
        @Size(max = 120) String playerId,
        Boolean completed,
        Integer room,
        Integer taskId
) {
}
