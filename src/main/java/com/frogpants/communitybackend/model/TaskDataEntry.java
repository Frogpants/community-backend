package com.frogpants.communitybackend.model;

public record TaskDataEntry(
        String userName,
        String name,
        String taskName,
        int room,
        boolean completed
) {
}
