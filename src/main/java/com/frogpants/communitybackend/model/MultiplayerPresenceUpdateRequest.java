package com.frogpants.communitybackend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MultiplayerPresenceUpdateRequest(
        @NotBlank @Size(max = 32) String playerName,
        @NotNull Double x,
        @NotNull Double y
) {
}
