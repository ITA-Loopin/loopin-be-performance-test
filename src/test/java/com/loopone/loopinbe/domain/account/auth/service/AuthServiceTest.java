package com.loopone.loopinbe.domain.account.auth.service;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.auth.dto.req.LoginRequest;
import com.loopone.loopinbe.domain.account.auth.dto.res.LoginResponse;
import com.loopone.loopinbe.domain.account.auth.serviceImpl.AuthServiceImpl;
import com.loopone.loopinbe.domain.account.member.dto.req.MemberCreateRequest;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.account.member.service.MemberService;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import com.loopone.loopinbe.global.security.JwtTokenProvider;
import com.loopone.loopinbe.global.webSocket.util.WsSessionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenService refreshTokenService;
    @Mock
    MemberService memberService;
    @Mock
    WsSessionRegistry wsSessionRegistry;
    @Mock AccessTokenDenyListService accessTokenDenyListService;

    @InjectMocks
    AuthServiceImpl authService;

    // ====== 헬퍼 ======
    private Member member(Long id, String email) {
        return Member.builder()
                .id(id)
                .email(email)
                .nickname("jun")
                .oAuthProvider(Member.OAuthProvider.GOOGLE)
                .providerId("pid")
                .build();
    }

    private CurrentUserDto cu(Long id, String email) {
        return new CurrentUserDto(
                id,
                email,
                null,
                "jun",
                null,
                null,
                null,
                null,
                Member.State.NORMAL,
                Member.MemberRole.ROLE_USER,
                Member.OAuthProvider.GOOGLE,
                "pid"
        );
    }

    // ====== login ======
    @Test
    @DisplayName("login: 이메일로 멤버 조회 후 Access/Refresh 토큰 발급 + Refresh Redis 저장")
    void login_success() {
        // given
        var email = "jun@loop.in";
        var m = member(1L, email);

        given(memberRepository.findByEmail(email))
                .willReturn(Optional.of(m));
        given(jwtTokenProvider.generateToken(eq(email), eq("ACCESS"), any()))
                .willReturn("access-token-123");
        given(jwtTokenProvider.generateToken(eq(email), eq("REFRESH"), any()))
                .willReturn("refresh-token-456");

        var req = LoginRequest.builder()
                .email(email)
                .build();

        // when
        LoginResponse resp = authService.login(req);

        // then
        assertThat(resp.getAccessToken()).isEqualTo("access-token-123");
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-token-456");

        verify(memberRepository).findByEmail(email);
        verify(jwtTokenProvider).generateToken(eq(email), eq("ACCESS"), any());
        verify(jwtTokenProvider).generateToken(eq(email), eq("REFRESH"), any());
        verify(refreshTokenService).saveRefreshToken(
                eq("jun@loop.in"),
                eq("refresh-token-456"),
                any()
        );
    }

    @Test
    @DisplayName("login: 멤버가 없으면 USER_NOT_FOUND 예외 발생")
    void login_userNotFound() {
        // given
        var email = "no@loop.in";
        given(memberRepository.findByEmail(email))
                .willReturn(Optional.empty());

        var req = LoginRequest.builder()
                .email(email)
                .build();

        // when & then
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining(ReturnCode.USER_NOT_FOUND.getMessage());

        verify(memberRepository).findByEmail(email);
        verify(jwtTokenProvider, never()).generateToken(anyString(), anyString(), any());
        verify(refreshTokenService, never()).saveRefreshToken(anyString(), anyString(), any());
    }

    // ====== signUpAndLogin ======
//    @Test
//    @DisplayName("signUpAndLogin: regularSignUp 이후 login 로직 재사용해서 토큰 발급")
//    void signUpAndLogin_success() {
//        // given
//        var req = new MemberCreateRequest(
//                "jun@loop.in",
//                "jun",
//                Member.OAuthProvider.GOOGLE,
//                "pid"
//        );
//        var registered = member(1L, "jun@loop.in");
//
//        given(memberService.regularSignUp(req)).willReturn(registered);
//        given(memberRepository.findByEmail("jun@loop.in"))
//                .willReturn(Optional.of(registered));
//        given(jwtTokenProvider.generateToken(eq("jun@loop.in"), eq("ACCESS"), any()))
//                .willReturn("access-token-123");
//        given(jwtTokenProvider.generateToken(eq("jun@loop.in"), eq("REFRESH"), any()))
//                .willReturn("refresh-token-456");
//
//        // when
//        LoginResponse resp = authService.signUpAndLogin(req);
//
//        // then
//        assertThat(resp.getAccessToken()).isEqualTo("access-token-123");
//        assertThat(resp.getRefreshToken()).isEqualTo("refresh-token-456");
//
//        // regularSignUp 호출 + login 에서 사용하는 의존성들 호출 검증
//        verify(memberService).regularSignUp(req);
//        verify(memberRepository).findByEmail("jun@loop.in");
//        verify(jwtTokenProvider).generateToken(eq("jun@loop.in"), eq("ACCESS"), any());
//        verify(jwtTokenProvider).generateToken(eq("jun@loop.in"), eq("REFRESH"), any());
//        verify(refreshTokenService).saveRefreshToken(eq("1"), eq("refresh-token-456"), any());
//    }

    // ====== logout ======
    @Test
    @DisplayName("logout: Refresh 삭제 + access deny-list 등록 + WS 세션 종료")
    void logout_success() {
        // given
        var currentUser = cu(1L, "jun@loop.in");
        var accessToken = "access-token-123";

        given(jwtTokenProvider.validateAccessToken(accessToken)).willReturn(true);
        given(jwtTokenProvider.getJti(accessToken)).willReturn("jti-123");
        given(jwtTokenProvider.getRemainingSeconds(accessToken)).willReturn(3600L);
        given(wsSessionRegistry.count(1L)).willReturn(2);

        // when
        authService.logout(currentUser, accessToken);

        // then
        verify(refreshTokenService).deleteRefreshToken("1");
        verify(jwtTokenProvider).validateAccessToken(accessToken);
        verify(jwtTokenProvider).getJti(accessToken);
        verify(jwtTokenProvider).getRemainingSeconds(accessToken);
        verify(accessTokenDenyListService).deny("jti-123", Duration.ofSeconds(3600L));

        verify(wsSessionRegistry).closeAll(eq(1L),
                argThat(status -> status.getCode() == 4401));
        verify(wsSessionRegistry).count(1L);
    }

    @Test
    @DisplayName("logout: accessToken이 null 이거나 유효하지 않으면 deny-list에 추가하지 않음")
    void logout_invalidAccessToken() {
        // given
        var currentUser = cu(1L, "jun@loop.in");

        given(jwtTokenProvider.validateAccessToken(anyString())).willReturn(false);

        // when
        authService.logout(currentUser, "invalid-token");

        // then
        verify(refreshTokenService).deleteRefreshToken("1");
        verify(jwtTokenProvider).validateAccessToken("invalid-token");
        verify(accessTokenDenyListService, never()).deny(anyString(), any());
    }

    // ====== refreshToken ======
    @Test
    @DisplayName("refreshToken: Bearer 제거 후 저장된 토큰과 일치 & 유효하면 새로운 Access 발급")
    void refreshToken_success() {
        // given
        var currentUser = cu(1L, "jun@loop.in");
        var rawRefresh = "refresh-token-456";
        var bearerRefresh = "Bearer " + rawRefresh;

        given(refreshTokenService.getRefreshToken("1")).willReturn(rawRefresh);
        given(jwtTokenProvider.validateRefreshToken(rawRefresh)).willReturn(true);
        given(jwtTokenProvider.getEmailFromToken(rawRefresh)).willReturn("jun@loop.in");
        given(jwtTokenProvider.generateToken(eq("jun@loop.in"), eq("ACCESS"), any()))
                .willReturn("new-access-token");

        // when
        LoginResponse resp = authService.refreshToken(bearerRefresh, currentUser);

        // then
        assertThat(resp.getAccessToken()).isEqualTo("new-access-token");
        assertThat(resp.getRefreshToken()).isEqualTo(rawRefresh);

        verify(refreshTokenService).getRefreshToken("1");
        verify(jwtTokenProvider).validateRefreshToken(rawRefresh);
        verify(jwtTokenProvider).getEmailFromToken(rawRefresh);
        verify(jwtTokenProvider).generateToken(eq("jun@loop.in"), eq("ACCESS"), any());
    }

    @Test
    @DisplayName("refreshToken: 저장된 Refresh 토큰이 없거나 다르면 예외")
    void refreshToken_notMatched() {
        // given
        var currentUser = cu(1L, "jun@loop.in");

        given(refreshTokenService.getRefreshToken("1"))
                .willReturn("other-refresh-token");

        // when & then
        assertThatThrownBy(() -> authService.refreshToken("refresh-token-456", currentUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("유효하지 않은 리프레시 토큰");

        verify(jwtTokenProvider, never()).validateRefreshToken(anyString());
        verify(jwtTokenProvider, never()).generateToken(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("refreshToken: 저장된 Refresh 토큰이 만료되면 예외")
    void refreshToken_expired() {
        // given
        var currentUser = cu(1L, "jun@loop.in");
        var stored = "refresh-token-456";

        given(refreshTokenService.getRefreshToken("1")).willReturn(stored);
        given(jwtTokenProvider.validateRefreshToken(stored)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.refreshToken(stored, currentUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("리프레시 토큰이 만료되었습니다.");

        verify(jwtTokenProvider).validateRefreshToken(stored);
        verify(jwtTokenProvider, never()).generateToken(anyString(), anyString(), any());
    }
}
