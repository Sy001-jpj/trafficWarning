package com.trafficwarning.backend.dto;

public record UserCreateRequest(
        String username,
        String displayName,
        String phone,
        String email,
        String role,
        String status
) {
}
