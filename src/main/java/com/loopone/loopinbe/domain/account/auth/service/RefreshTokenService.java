package com.loopone.loopinbe.domain.account.auth.service;

import java.time.Duration;

public interface RefreshTokenService {
    // Refresh Token 저장
    void saveRefreshToken(String email, String refreshToken, Duration duration);

    // Refresh Token 조회
    String getRefreshToken(String email);

    // Refresh Token 삭제
    void deleteRefreshToken(String email);
}
