package com.frogpants.communitybackend.model;

import java.util.List;

public record LeaderboardResponse(List<ScoreEntry> entries) {
}