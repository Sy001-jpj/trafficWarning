package com.trafficwarning.backend.dto;

import java.time.LocalDateTime;

public record UserView(
        Long id,
        String username,
        String displayName,
        String phone,
        String email,
        String role,
        String status,
        LocalDateTime createdAt
) {
}
