package com.frogpants.communitybackend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MultiplayerJoinRoomRequest(
        @NotBlank @Size(max = 32) String playerName
) {
}
