package com.frogpants.communitybackend.model;

import java.util.List;

public record MultiplayerPresenceSnapshot(
        String roomCode,
        List<MultiplayerPresenceEntry> players
) {
}
