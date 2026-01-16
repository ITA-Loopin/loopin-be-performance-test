package com.loopone.loopinbe.global.kafka.event.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.auth.dto.AuthPayload;
import com.loopone.loopinbe.domain.account.auth.service.AccessTokenDenyListService;
import com.loopone.loopinbe.domain.account.auth.service.AuthService;
import com.loopone.loopinbe.domain.account.auth.service.RefreshTokenService;
import com.loopone.loopinbe.domain.account.member.converter.MemberConverter;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import com.loopone.loopinbe.global.webSocket.util.WsSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.loopone.loopinbe.global.constants.KafkaKey.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthEventConsumer {
    private final ObjectMapper objectMapper;
    private final AuthService authService;
    private final MemberRepository memberRepository;
    private final MemberConverter memberConverter;

    @KafkaListener(topics = LOGOUT_TOPIC, groupId = AUTH_GROUP_ID, containerFactory = KAFKA_LISTENER_CONTAINER)
    public void consumeLogout(ConsumerRecord<String, String> rec) {
        try {
            AuthPayload payload = objectMapper.readValue(rec.value(), AuthPayload.class);
            Long memberId = payload.memberId();
            Member member = memberRepository.findById(memberId)
                    .orElseThrow(() -> new ServiceException(ReturnCode.USER_NOT_FOUND));
            CurrentUserDto currentUser = memberConverter.toCurrentUserDto(member);
            // 기존 logout 로직 재사용
            authService.logout(currentUser, payload.accessToken());
        } catch (Exception e) {
            log.error("Failed to handle Auth logout event", e);
            throw new RuntimeException(e);
        }
    }
}
