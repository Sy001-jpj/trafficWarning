package com.trafficwarning.backend.dto;

public record UserUpdateRequest(
        String displayName,
        String phone,
        String email,
        String role,
        String status
) {
}
