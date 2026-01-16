package com.loopone.loopinbe.global.kafka.event.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.chat.chatMessage.converter.ChatMessageConverter;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.res.ChatMessageResponse;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatMessagePayload;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.type.MessageType;
import com.loopone.loopinbe.domain.chat.chatMessage.service.ChatMessageService;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomService;
import com.loopone.loopinbe.domain.loop.ai.dto.AiPayload;
import com.loopone.loopinbe.domain.loop.ai.dto.res.RecommendationsLoop;
import com.loopone.loopinbe.domain.loop.ai.service.LoopAIService;
import com.loopone.loopinbe.domain.sse.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.loopone.loopinbe.global.constants.Constant.AI_CREATE_MESSAGE;
import static com.loopone.loopinbe.global.constants.Constant.AI_UPDATE_SUCCESS_MESSAGE;
import static com.loopone.loopinbe.global.constants.KafkaKey.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiEventConsumer {
    private final ObjectMapper objectMapper;
    private final LoopAIService loopAIService;
    private final SseEmitterService sseEmitterService;
    private final ChatMessageService chatMessageService;
    private final ChatMessageConverter chatMessageConverter;
    private final ChatRoomService chatRoomService;

    @KafkaListener(topics = OPEN_AI_CREATE_TOPIC, groupId = OPEN_AI_GROUP_ID, containerFactory = KAFKA_LISTENER_CONTAINER)
    public void consumeAiCreateLoop(ConsumerRecord<String, String> rec) {
        handleAiEvent(rec, AI_CREATE_MESSAGE);
    }

    @KafkaListener(topics = OPEN_AI_UPDATE_TOPIC, groupId = OPEN_AI_GROUP_ID, containerFactory = KAFKA_LISTENER_CONTAINER)
    public void consumeAiUpdateLoop(ConsumerRecord<String, String> rec) {
        handleAiEvent(rec, AI_UPDATE_SUCCESS_MESSAGE);
    }

    private void handleAiEvent(ConsumerRecord<String, String> rec, String defaultMessage) {
        try {
            AiPayload req = objectMapper.readValue(rec.value(), AiPayload.class);

            loopAIService.chat(req)
                    .thenAccept(recommendations -> processAiResponse(req, recommendations, defaultMessage))
                    .exceptionally(ex -> {
                        log.error("AI 응답 처리 중 오류 발생: {}", ex.getMessage(), ex);
                        return null;
                    });

        } catch (JsonProcessingException e) {
            log.error("AI 이벤트 메시지 파싱 실패: {}", rec.value(), e);
        } catch (Exception e) {
            log.error("AI 이벤트 처리 실패", e);
        }
    }

    private void processAiResponse(AiPayload req, RecommendationsLoop recommendations, String message) {
        // 1) AI 결과 기반 Inbound 메시지 생성
        ChatMessagePayload inbound = createBotPayload(req, recommendations, message);

        // 2) SSE 전송 (클라이언트에게 먼저 보여줌)
        sendSseEvent(inbound);

        // 3) DB 저장
        chatMessageService.processInbound(inbound);

        // 채팅방 제목 변경
        chatRoomService.updateChatRoomTitle(req.chatRoomId(), recommendations.title());
    }

    private ChatMessagePayload createBotPayload(AiPayload req, RecommendationsLoop recommendationsLoop, String message) {
        return new ChatMessagePayload(
                generateDeterministicKey(req),
                req.clientMessageId(),
                req.chatRoomId(),
                null, // Bot has no memberId
                message,
                null,
                recommendationsLoop.recommendations(),
                recommendationsLoop.loopRuleId(),
                null,
                ChatMessage.AuthorType.BOT,
                true,
                Instant.now(),
                Instant.now()
        );
    }

    private void sendSseEvent(ChatMessagePayload inbound) {
        try {
            Map<Long, Member> memberMap = chatMessageConverter.loadMembersFromPayload(List.of(inbound));
            ChatMessageResponse response = chatMessageConverter.toChatMessageResponse(inbound, memberMap);
            sseEmitterService.sendToClient(inbound.chatRoomId(), MessageType.MESSAGE, response);
        } catch (Exception e) {
            // SSE 전송 실패가 로직 전체 실패로 이어지지 않도록 로그만 기록
            log.warn("SSE 이벤트 전송 실패 (ChatRoomId: {}): {}", inbound.chatRoomId(), e.getMessage());
        }
    }

    private String generateDeterministicKey(AiPayload req) {
        return "ai:" + req.clientMessageId();
    }
}
