package com.frogpants.communitybackend.model;

import java.time.Instant;

public record ScoreEntry(
        String playerId,
        String playerName,
        long score,
        int level,
        Instant submittedAt
) {
}