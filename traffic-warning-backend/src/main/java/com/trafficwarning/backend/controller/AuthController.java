package com.trafficwarning.backend.controller;

import com.trafficwarning.backend.common.ApiResponse;
import com.trafficwarning.backend.dto.AuthLoginRequest;
import com.trafficwarning.backend.dto.AuthRegisterRequest;
import com.trafficwarning.backend.dto.AuthResponse;
import com.trafficwarning.backend.dto.UserView;
import com.trafficwarning.backend.repository.UserRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String DISABLED_STATUS = "\u7981\u7528";

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody AuthLoginRequest request) {
        String username = normalize(request.username());
        String password = normalize(request.password());
        if (username.isBlank() || password.isBlank()) {
            return ApiResponse.fail(400, "\u8bf7\u8f93\u5165\u7528\u6237\u540d\u548c\u5bc6\u7801");
        }

        return userRepository.findByUsernameAndPassword(username, password)
                .map(this::buildLoginResponse)
                .orElseGet(() -> ApiResponse.fail(401, "\u7528\u6237\u540d\u6216\u5bc6\u7801\u9519\u8bef"));
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@RequestBody AuthRegisterRequest request) {
        String username = normalize(request.username());
        String password = normalize(request.password());
        String confirmPassword = normalize(request.confirmPassword());

        if (username.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            return ApiResponse.fail(400, "\u8bf7\u5b8c\u6574\u586b\u5199\u7528\u6237\u540d\u548c\u5bc6\u7801");
        }
        if (password.length() < 6) {
            return ApiResponse.fail(400, "\u5bc6\u7801\u957f\u5ea6\u81f3\u5c11 6 \u4f4d");
        }
        if (!password.equals(confirmPassword)) {
            return ApiResponse.fail(400, "\u4e24\u6b21\u8f93\u5165\u7684\u5bc6\u7801\u4e0d\u4e00\u81f4");
        }
        if (userRepository.existsByUsername(username)) {
            return ApiResponse.fail(409, "\u8be5\u7528\u6237\u540d\u5df2\u88ab\u6ce8\u518c");
        }

        UserView user = userRepository.createUser(username, password);
        return ApiResponse.success(new AuthResponse(createDemoToken(user), user));
    }

    private ApiResponse<AuthResponse> buildLoginResponse(UserView user) {
        if (DISABLED_STATUS.equals(user.status())) {
            return ApiResponse.fail(403, "\u5f53\u524d\u8d26\u53f7\u5df2\u88ab\u7981\u7528\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458");
        }
        return ApiResponse.success(new AuthResponse(createDemoToken(user), user));
    }

    private String createDemoToken(UserView user) {
        return "traffic-token-" + user.id() + "-" + System.currentTimeMillis();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
