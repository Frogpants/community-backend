package com.frogpants.communitybackend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskDataRequest(
        @NotBlank @Size(max = 120) String name,
        Boolean completed,
        Integer room,
        Integer taskId
) {
}
