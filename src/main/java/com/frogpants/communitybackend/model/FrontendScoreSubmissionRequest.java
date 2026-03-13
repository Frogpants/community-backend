package com.frogpants.communitybackend.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FrontendScoreSubmissionRequest(
        @NotBlank @Size(max = 32) String playerName,
        @Min(0) long score,
        @Min(1) int level
) {
}