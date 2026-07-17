package com.trafficwarning.backend.controller;

import com.trafficwarning.backend.common.ApiResponse;
import com.trafficwarning.backend.dto.UserCreateRequest;
import com.trafficwarning.backend.dto.UserStatusRequest;
import com.trafficwarning.backend.dto.UserUpdateRequest;
import com.trafficwarning.backend.dto.UserView;
import com.trafficwarning.backend.repository.UserRepository;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final String DEFAULT_ROLE = "\u666e\u901a\u7528\u6237";
    private static final String DEFAULT_STATUS = "\u6b63\u5e38";

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<List<UserView>> listUsers() {
        return ApiResponse.success(userRepository.findAll());
    }

    @PostMapping
    public ApiResponse<UserView> createUser(@RequestBody UserCreateRequest request) {
        String username = normalize(request.username());
        String displayName = normalize(request.displayName());
        if (username.isBlank()) {
            return ApiResponse.fail(400, "\u8bf7\u586b\u5199\u7528\u6237\u540d");
        }
        if (displayName.isBlank()) {
            return ApiResponse.fail(400, "\u8bf7\u586b\u5199\u771f\u5b9e\u59d3\u540d");
        }
        if (userRepository.existsByUsername(username)) {
            return ApiResponse.fail(409, "\u8be5\u7528\u6237\u540d\u5df2\u5b58\u5728");
        }

        UserView user = userRepository.createManagedUser(
                username,
                displayName,
                normalize(request.phone()),
                normalize(request.email()),
                defaultIfBlank(request.role(), DEFAULT_ROLE),
                defaultIfBlank(request.status(), DEFAULT_STATUS)
        );
        return ApiResponse.success(user);
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> updateUser(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        String displayName = normalize(request.displayName());
        if (displayName.isBlank()) {
            return ApiResponse.fail(400, "\u8bf7\u586b\u5199\u771f\u5b9e\u59d3\u540d");
        }

        return userRepository.updateUser(
                id,
                displayName,
                normalize(request.phone()),
                normalize(request.email()),
                defaultIfBlank(request.role(), DEFAULT_ROLE),
                defaultIfBlank(request.status(), DEFAULT_STATUS)
        ).map(ApiResponse::success).orElseGet(() -> ApiResponse.fail(404, "\u7528\u6237\u4e0d\u5b58\u5728"));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserView> updateStatus(@PathVariable Long id, @RequestBody UserStatusRequest request) {
        String status = defaultIfBlank(request.status(), DEFAULT_STATUS);
        return userRepository.updateStatus(id, status)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(404, "\u7528\u6237\u4e0d\u5b58\u5728"));
    }

    @PatchMapping("/{id}/password/reset")
    public ApiResponse<UserView> resetPassword(@PathVariable Long id) {
        return userRepository.resetPassword(id)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.fail(404, "\u7528\u6237\u4e0d\u5b58\u5728"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> deleteUser(@PathVariable Long id) {
        boolean deleted = userRepository.deleteById(id);
        return deleted
                ? ApiResponse.success(true)
                : ApiResponse.fail(404, "\u7528\u6237\u4e0d\u5b58\u5728");
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
