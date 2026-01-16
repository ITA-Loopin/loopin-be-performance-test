package com.loopone.loopinbe.domain.account.auth.service;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.auth.dto.req.LoginRequest;
import com.loopone.loopinbe.domain.account.auth.dto.res.LoginResponse;
import com.loopone.loopinbe.domain.account.member.dto.req.MemberCreateRequest;

public interface AuthService {
    // 회원가입 후 로그인 처리
    LoginResponse signUpAndLogin(String nickname, String ticket);

    // 로그인
    LoginResponse login(LoginRequest loginRequest);

    // 로그아웃
    void logout(CurrentUserDto currentUser, String accessToken);

    // accessToken 재발급
    LoginResponse refreshToken(String refreshToken);
}
