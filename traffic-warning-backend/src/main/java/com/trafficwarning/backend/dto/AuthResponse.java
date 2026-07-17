package com.trafficwarning.backend.dto;

public record AuthResponse(
        String token,
        UserView user
) {
}
