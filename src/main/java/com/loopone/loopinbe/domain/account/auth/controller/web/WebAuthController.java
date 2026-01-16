package com.loopone.loopinbe.domain.account.auth.controller.web;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUser;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.auth.dto.req.LoginRequest;
import com.loopone.loopinbe.domain.account.auth.dto.req.OAuthLoginRequest;
import com.loopone.loopinbe.domain.account.auth.dto.res.LoginResponse;
import com.loopone.loopinbe.domain.account.auth.service.AuthService;
import com.loopone.loopinbe.global.common.response.ApiResponse;
import com.loopone.loopinbe.global.web.cookie.WebAuthCookieFactory;
import com.loopone.loopinbe.global.security.TokenResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest-api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "WebAuth", description = """
웹 일반 인증 API

소셜 로그인 플로우:
1. 프론트에서 https://api.loopin.co.kr/oauth2/authorization/{provider}로 요청
   - provider: google, kakao, naver
2. 로그인 성공 시 백엔드에서 쿠키 발급
""")
public class WebAuthController {
    private final AuthService authService;
    private final WebAuthCookieFactory webAuthCookieFactory;
    private final TokenResolver tokenResolver;

    // 회원가입 후 로그인 처리
    @PostMapping("/signup-login")
    @Operation(summary = "회원가입 후 로그인 처리", description = "신규 사용자 회원가입 후 로그인 처리합니다.")
    public ResponseEntity<ApiResponse<Void>> signUpAndLogin(@Valid @RequestBody OAuthLoginRequest oAuthLoginRequest) {
        LoginResponse login = authService.signUpAndLogin(oAuthLoginRequest.nickname(), oAuthLoginRequest.ticket());
        ResponseCookie accessCookie = webAuthCookieFactory.issueAccess(login.getAccessToken());
        ResponseCookie refreshCookie = webAuthCookieFactory.issueRefresh(login.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success());
    }

    // 로그인
    @PostMapping("/login")
    @Operation(summary = "로그인", description = "이메일로 로그인합니다.")
    public ResponseEntity<ApiResponse<Void>> login(@RequestBody @Valid LoginRequest loginRequest) {
        LoginResponse login = authService.login(loginRequest);
        ResponseCookie accessCookie = webAuthCookieFactory.issueAccess(login.getAccessToken());
        ResponseCookie refreshCookie = webAuthCookieFactory.issueRefresh(login.getRefreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success());
    }

    // 로그아웃
    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 로그인된 사용자가 로그아웃합니다.")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request,
                                                    @CurrentUser CurrentUserDto currentUser) {
        String accessToken = tokenResolver.resolveAccess(request);
        authService.logout(currentUser, accessToken);

        ResponseCookie accessCookie = webAuthCookieFactory.expireAccess();
        ResponseCookie refreshCookie = webAuthCookieFactory.expireRefresh();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success());
    }

    // accessToken 재발급
    @GetMapping("/refresh-token")
    @Operation(summary = "accessToken 재발급", description = "refresh 토큰을 사용하여 access 토큰을 재발급합니다.")
    public ResponseEntity<ApiResponse<Void>> refreshToken(HttpServletRequest request) {
        String refreshToken = tokenResolver.resolveRefresh(request);
        LoginResponse refreshed = authService.refreshToken(refreshToken);

        ResponseCookie accessCookie = webAuthCookieFactory.issueAccess(refreshed.getAccessToken()); // access만 갱신
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .body(ApiResponse.success());
    }
}
