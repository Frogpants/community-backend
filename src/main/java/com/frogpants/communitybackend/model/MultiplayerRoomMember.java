package com.frogpants.communitybackend.model;

import java.time.Instant;

public record MultiplayerRoomMember(
        String playerId,
        String playerName,
        Instant joinedAt
) {
}
