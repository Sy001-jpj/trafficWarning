package com.trafficwarning.backend.dto;

public record AuthRegisterRequest(
        String username,
        String password,
        String confirmPassword
) {
}
