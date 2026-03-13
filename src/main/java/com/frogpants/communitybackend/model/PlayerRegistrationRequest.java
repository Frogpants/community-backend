package com.frogpants.communitybackend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlayerRegistrationRequest(
        @NotBlank @Size(max = 32) String playerName
) {
}