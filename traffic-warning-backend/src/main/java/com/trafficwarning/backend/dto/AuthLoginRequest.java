package com.trafficwarning.backend.dto;

public record AuthLoginRequest(
        String username,
        String password
) {
}
