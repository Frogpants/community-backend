package com.frogpants.communitybackend.model;

import java.time.Instant;

public record PlayerSession(String playerId, String playerName, Instant registeredAt) {
}