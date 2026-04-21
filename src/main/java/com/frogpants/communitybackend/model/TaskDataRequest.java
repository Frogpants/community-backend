package com.frogpants.communitybackend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskDataRequest(
        @JsonAlias({"task", "title", "taskTitle", "objectiveName"})
        @Size(max = 120) String name,
        @JsonAlias({"task_name", "taskText", "objective"})
        @Size(max = 120) String taskName,
        @JsonAlias({"taskDisplayName", "taskLabel", "simplifiedTaskName", "displayTaskName", "task_display_name"})
        @Size(max = 120) String simplifiedTaskName,
        @JsonAlias({"username", "user_name", "user"})
        @Size(max = 120) String userName,
        @JsonAlias({"playerName", "displayName", "characterName", "characterDisplayName", "player_display_name"})
        @Size(max = 120) String characterDisplayName,
        @JsonAlias({"playerID", "userId", "userID", "characterId", "characterID"})
        @Size(max = 120) String playerId,
        @JsonAlias({"isCompleted", "taskCompleted", "isTaskCompleted", "complete", "done"})
        Boolean completed,
        @JsonAlias({"roomId", "roomID", "roomIndex"})
        Integer room,
        @JsonAlias({"roomNumber", "roomNum", "currentRoom"})
        Integer roomNumber,
        @JsonAlias({"taskID", "objectiveId", "objectiveID"})
        Integer taskId,
        @JsonAlias({"taskNumber", "taskIndex", "taskNum"})
        Integer taskNumber
) {
}
