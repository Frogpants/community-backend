package com.frogpants.communitybackend.model;

import java.time.Instant;

public record MultiplayerPresenceEntry(
        String playerId,
        String playerName,
        double x,
        double y,
        Instant updatedAt
) {
}
