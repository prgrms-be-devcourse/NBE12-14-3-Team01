package com.merge.backend.domain.user.controller;

import com.merge.backend.domain.user.dto.LoginRequest;
import com.merge.backend.domain.user.dto.LoginResponse;
import com.merge.backend.domain.user.dto.RefreshResponse;
import com.merge.backend.domain.user.dto.SignupRequest;
import com.merge.backend.domain.user.dto.UserResponse;
import com.merge.backend.domain.user.entity.User;
import com.merge.backend.domain.user.service.UserService;
import com.merge.backend.global.dto.ApiResponse;
import com.merge.backend.global.rq.Rq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class ApiV1UserController {

    private final UserService userService;
    private final Rq rq;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(
        @Valid @RequestBody SignupRequest request) {
        User user = userService.join(request.getEmail(), request.getPassword(), request.getName());

        UserResponse response = UserResponse.from(user);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success("201", "회원가입이 완료되었습니다.", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request) {
        User user = userService.login(request.email(), request.password());
        String accessToken = userService.genAccessToken(user);
        String refreshToken = userService.issueRefreshToken(user);

        rq.addCookie("refreshToken", refreshToken);

        LoginResponse response = new LoginResponse(UserResponse.from(user), accessToken);

        return ResponseEntity.ok(ApiResponse.success("200", "로그인에 성공했습니다", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh() {
        String refreshToken = rq.getCookieValue("refreshToken", "");
        String accessToken = userService.refreshAccessToken(refreshToken);

        return ResponseEntity.ok(
            ApiResponse.success("200", "Access Token이 재발급되었습니다", new RefreshResponse(accessToken))
        );
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        User user = userService.getById(rq.getActorId());
        UserResponse response = UserResponse.from(user);

        return ResponseEntity.ok(ApiResponse.success("200", "현재 사용자 정보를 조회했습니다", response));
    }

}
