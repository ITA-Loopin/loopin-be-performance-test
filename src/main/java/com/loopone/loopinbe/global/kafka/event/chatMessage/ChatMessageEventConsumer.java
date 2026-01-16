package com.loopone.loopinbe.global.kafka.event.chatMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.chat.chatMessage.converter.ChatMessageConverter;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatMessagePayload;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.res.ChatMessageResponse;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.type.MessageType;
import com.loopone.loopinbe.domain.chat.chatMessage.service.ChatMessageService;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomMemberService;
import com.loopone.loopinbe.global.webSocket.handler.ChatWebSocketHandler;
import com.loopone.loopinbe.global.webSocket.payload.ChatWebSocketPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.loopone.loopinbe.global.constants.KafkaKey.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageEventConsumer {
    private final ObjectMapper objectMapper;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final ChatRoomMemberService chatRoomMemberService;
    private final ChatMessageService chatMessageService;
    private final ChatMessageConverter chatMessageConverter;

    @KafkaListener(
            topics = {CHAT_MESSAGE_TOPIC, CHAT_READ_UP_TO_TOPIC, CHAT_DELETE_TOPIC},
            groupId = CHAT_GROUP_ID,
            containerFactory = KAFKA_LISTENER_CONTAINER
    )
    public void consumeWsEvent(ConsumerRecord<String, String> rec) {
        try {
            ChatWebSocketPayload event = objectMapper.readValue(rec.value(), ChatWebSocketPayload.class);
            Long chatRoomId = event.getChatRoomId();
            if (chatRoomId == null) {
                log.warn("WS event missing chatRoomId. topic={}, key={}", rec.topic(), rec.key());
                return;
            }
            switch (event.getMessageType()) {
                case MESSAGE -> {
                    // 1) validate
                    Long memberId = event.getMemberId();
                    UUID clientMessageId = event.getClientMessageId();
                    String content = (event.getChatMessageResponse() != null)
                            ? event.getChatMessageResponse().getContent()
                            : null;
                    if (memberId == null || clientMessageId == null || content == null || content.isBlank()) {
                        log.warn("Invalid MESSAGE event. roomId={}, memberId={}, clientMessageId={}, hasContent={}",
                                chatRoomId, memberId, clientMessageId, content != null);
                        return;
                    }
                    // 2) inbound payload 생성 (idempotent key)
                    Instant now = Instant.now();
                    ChatMessagePayload inbound = new ChatMessagePayload(
                            "u:" + clientMessageId,
                            clientMessageId,
                            chatRoomId,
                            memberId,
                            content,
                            null,
                            null,
                            null,
                            null,
                            ChatMessage.AuthorType.USER,
                            false,
                            now,
                            now
                    );
                    // 3) Mongo upsert (멱등 저장)
                    ChatMessagePayload saved = chatMessageService.processInbound(inbound);
                    // 4) WS 응답 DTO로 매핑 (저장된 결과 기준)
                    Map<Long, Member> memberMap = chatMessageConverter.loadMembersFromPayload(List.of(saved));
                    ChatMessageResponse savedResp = chatMessageConverter.toChatMessageResponse(saved, memberMap);
                    ChatWebSocketPayload out = ChatWebSocketPayload.builder()
                            .messageType(MessageType.MESSAGE)
                            .chatRoomId(chatRoomId)
                            .memberId(memberId)
                            .clientMessageId(saved.clientMessageId())
                            .chatMessageResponse(savedResp)
                            .build();
                    String payloadJson = objectMapper.writeValueAsString(out);
                    chatWebSocketHandler.broadcastToRoom(chatRoomId, payloadJson);
                }
                case READ_UP_TO -> {
                    Long memberId = event.getMemberId();
                    Instant lastReadAt = event.getLastReadAt();
                    if (memberId == null || lastReadAt == null) {
                        log.warn("Invalid READ_UP_TO event. roomId={}, memberId={}, lastReadAt={}",
                                chatRoomId, memberId, lastReadAt);
                        return;
                    }
                    // 동기 업데이트
                    Instant updated = chatRoomMemberService.updateLastReadAt(chatRoomId, memberId, lastReadAt);
                    ChatWebSocketPayload out = ChatWebSocketPayload.builder()
                            .messageType(MessageType.READ_UP_TO)
                            .chatRoomId(chatRoomId)
                            .memberId(memberId)
                            .lastReadAt(updated)
                            .build();
                    String payloadJson = objectMapper.writeValueAsString(out);
                    chatWebSocketHandler.broadcastToRoom(chatRoomId, payloadJson);
                }
                case DELETE -> {
                    Long memberId = event.getMemberId();
                    String messageId = event.getDeleteId();
                    if (memberId == null || messageId == null) {
                        log.warn("Invalid DELETE event. roomId={}, memberId={}, messageId={}",
                                chatRoomId, memberId, messageId);
                        return;
                    }
                    // 동기 삭제
                    chatMessageService.deleteChatMessage(messageId, memberId);
                    ChatWebSocketPayload out = ChatWebSocketPayload.builder()
                            .messageType(MessageType.DELETE)
                            .chatRoomId(chatRoomId)
                            .memberId(memberId)
                            .deleteId(messageId)
                            .build();
                    String payloadJson = objectMapper.writeValueAsString(out);
                    chatWebSocketHandler.broadcastToRoom(chatRoomId, payloadJson);
                }
                default -> log.warn("Unhandled event type: {} topic={}, key={}",
                        event.getMessageType(), rec.topic(), rec.key());
            }
        } catch (Exception e) {
            // 여기서 throw 하면 Kafka가 재시도/리밸런싱 걸 수 있음.
            // "일시적 장애"면 throw가 맞고, "데이터 오류"면 return이 맞음.
            log.error("Failed to handle WS event. topic={}, key={}", rec.topic(), rec.key(), e);
            throw new RuntimeException(e);
        }
    }
}
