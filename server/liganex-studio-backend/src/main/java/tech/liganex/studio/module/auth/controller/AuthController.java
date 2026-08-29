package tech.liganex.studio.module.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.module.auth.dto.LoginRequest;
import tech.liganex.studio.module.auth.dto.RegisterRequest;
import tech.liganex.studio.module.auth.dto.TokenResponse;
import tech.liganex.studio.module.auth.dto.UserResponse;
import tech.liganex.studio.module.auth.service.AuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new tech.liganex.studio.common.BizException(tech.liganex.studio.common.ErrorCode.INVALID_TOKEN);
        }
        return ApiResponse.ok(authService.refresh(refreshToken));
    }
}
