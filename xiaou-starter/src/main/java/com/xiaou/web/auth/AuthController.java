package com.xiaou.web.auth;

import com.xiaou.web.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthLoginResult>> login(
            @Valid @RequestBody AuthLoginRequest request) {
        AuthLoginResult result = authService.login(request.username(), request.password());
        return ResponseEntity.ok(ApiResponse.success("登录成功", result));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<AuthLogoutResult>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        AuthLogoutResult result = authService.logout(extractBearerToken(authorization));
        return ResponseEntity.ok(ApiResponse.success("退出成功", result));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(401, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationFailure(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(400, "请求参数错误"));
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0,
                BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return "";
        }
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
}
