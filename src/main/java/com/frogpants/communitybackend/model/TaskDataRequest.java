package com.frogpants.communitybackend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

public record TaskDataRequest(
        @Size(max = 120) String name,
        @Size(max = 120) String taskName,
        @JsonAlias({"taskDisplayName", "taskLabel", "simplifiedTaskName", "displayTaskName"})
        @Size(max = 120) String simplifiedTaskName,
        @Size(max = 120) String userName,
        @JsonAlias({"playerName", "displayName", "characterName", "characterDisplayName"})
        @Size(max = 120) String characterDisplayName,
        @Size(max = 120) String playerId,
        Boolean completed,
        Integer room,
        @JsonAlias({"roomNumber", "roomNum", "currentRoom"})
        Integer roomNumber,
        Integer taskId,
        @JsonAlias({"taskNumber", "taskIndex"})
        Integer taskNumber
) {
}
