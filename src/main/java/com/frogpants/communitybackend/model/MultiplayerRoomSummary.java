package com.frogpants.communitybackend.model;

import java.time.Instant;

public record MultiplayerRoomSummary(
        String roomId,
        String roomCode,
        int playerCount,
        Instant createdAt
) {
}
