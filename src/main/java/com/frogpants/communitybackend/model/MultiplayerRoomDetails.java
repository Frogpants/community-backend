package com.frogpants.communitybackend.model;

import java.time.Instant;
import java.util.List;

public record MultiplayerRoomDetails(
        String roomId,
        String roomCode,
        String hostPlayerId,
        Instant createdAt,
        List<MultiplayerRoomMember> members
) {
}
