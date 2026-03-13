package com.frogpants.communitybackend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MultiplayerCreateRoomRequest(
        @NotBlank @Size(max = 32) String playerName
) {
}
