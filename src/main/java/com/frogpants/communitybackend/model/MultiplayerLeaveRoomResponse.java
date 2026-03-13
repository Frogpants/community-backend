package com.frogpants.communitybackend.model;

public record MultiplayerLeaveRoomResponse(
        String roomCode,
        boolean roomClosed,
        int playerCount
) {
}
