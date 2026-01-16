package com.loopone.loopinbe.global.webSocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.chat.chatMessage.converter.ChatMessageConverter;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatMessagePayload;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.res.ChatMessageResponse;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.type.MessageType;
import com.loopone.loopinbe.domain.chat.chatMessage.service.ChatMessageService;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomMemberService;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomService;
import com.loopone.loopinbe.global.kafka.event.chatMessage.ChatMessageEventPublisher;
import com.loopone.loopinbe.global.webSocket.payload.ChatWebSocketPayload;
import com.loopone.loopinbe.global.webSocket.util.WsSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final ChatRoomMemberService chatRoomMemberService;
    private final ChatMessageService chatMessageService;
    private final ChatMessageConverter chatMessageConverter;
    private final ObjectMapper objectMapper;
    private final ChatMessageEventPublisher chatMessageEventPublisher;
    private final Map<Long, CopyOnWriteArrayList<WebSocketSession>> chatRoomSessions = new ConcurrentHashMap<>();
    private final Map<WebSocketSession, Long> sessionRoomMap = new ConcurrentHashMap<>(); // 세션 -> 방 매핑
    private final WsSessionRegistry wsSessionRegistry;
    private final Map<WebSocketSession, Long> sessionMemberMap = new ConcurrentHashMap<>(); // 세션 → 멤버 매핑

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 인터셉터가 넣어준 memberId 필수
        Long memberId = (Long) session.getAttributes().get("memberId");
        if (memberId == null) {
            log.warn("WS connected without memberId: {}", session.getId());
            session.close(new CloseStatus(4401, "UNAUTHENTICATED"));
            return;
        }
        // 인터셉터가 넣어준 값 우선 사용
        Long chatRoomId = (Long) session.getAttributes().get("chatRoomId");
        if (chatRoomId == null) {
            chatRoomId = parseChatRoomIdFromQuery(session);
        }
        if (chatRoomId == null) {
            log.warn("WebSocket connected without chatRoomId: {}", session.getId());
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        // 방 멤버십 검증 - isBotRoom=false + membership를 동시에 검증
        boolean ok = chatRoomMemberService.canConnectNonBotRoom(chatRoomId, memberId);
        if (!ok) {
            log.warn("WS forbidden(non-bot only or not member): session={} memberId={} room={}", session.getId(), memberId, chatRoomId);
            sendWsErrorAndClose(session, "FORBIDDEN", "You can connect only to non-bot rooms you joined.");
            return;
        }
        chatRoomSessions.computeIfAbsent(chatRoomId, k -> new CopyOnWriteArrayList<>()).add(session);
        sessionRoomMap.put(session, chatRoomId);
        sessionMemberMap.put(session, memberId);
        wsSessionRegistry.add(memberId, session);
        log.info("WS connected: {} memberId={} chatRoomId={}", session.getId(), memberId, chatRoomId);
    }

    private Long parseChatRoomIdFromQuery(WebSocketSession session) {
        String q = session.getUri() != null ? session.getUri().getQuery() : null;
        if (q == null) return null;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            if (i > 0 && "chatRoomId".equals(p.substring(0, i))) {
                try {
                    return Long.parseLong(p.substring(i + 1));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private Long requireChatRoomId(WebSocketSession session) {
        Long chatRoomId = sessionRoomMap.get(session);
        if (chatRoomId == null) throw new IllegalStateException("WS session has no chatRoomId mapping");
        return chatRoomId;
    }

    private Long requireMemberId(WebSocketSession session) {
        Long memberId = sessionMemberMap.get(session);
        if (memberId == null) throw new IllegalStateException("WS session has no memberId mapping");
        return memberId;
    }

    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Long chatRoomId = requireChatRoomId(session);
            Long memberId = requireMemberId(session);
            ChatWebSocketPayload in = objectMapper.readValue(message.getPayload(), ChatWebSocketPayload.class);
            switch (in.getMessageType()) {
                case MESSAGE -> {
                    UUID clientMessageId = in.getClientMessageId();
                    if (clientMessageId == null) {
                        sendWsError(session, "BAD_REQUEST", "clientMessageId is required");
                        return;
                    }
                    String content = (in.getChatMessageResponse() != null)
                            ? in.getChatMessageResponse().getContent()
                            : null;
                    if (content == null || content.isBlank()) {
                        sendWsError(session, "BAD_REQUEST", "content is required");
                        return;
                    }
                    // 저장은 컨슈머가 함. 여기서는 이벤트만 발행.
                    ChatWebSocketPayload event = ChatWebSocketPayload.builder()
                            .messageType(MessageType.MESSAGE)
                            .chatRoomId(chatRoomId)
                            .memberId(memberId)
                            .clientMessageId(clientMessageId)
                            .chatMessageResponse(ChatMessageResponse.builder()
                                    .content(content)
                                    .build())
                            .build();
                    chatMessageEventPublisher.publishWsEvent(event);
                }
                case READ_UP_TO -> {
                    if (in.getLastReadAt() == null) {
                        sendWsError(session, "BAD_REQUEST", "lastReadAt is required");
                        return;
                    }
                    ChatWebSocketPayload event = ChatWebSocketPayload.builder()
                            .messageType(MessageType.READ_UP_TO)
                            .chatRoomId(chatRoomId)
                            .memberId(memberId)
                            .lastReadAt(in.getLastReadAt())
                            .build();
                    chatMessageEventPublisher.publishWsEvent(event);
                }
                case DELETE -> {
                    if (in.getDeleteId() == null) {
                        sendWsError(session, "BAD_REQUEST", "messageId is required");
                        return;
                    }
                    ChatWebSocketPayload event = ChatWebSocketPayload.builder()
                            .messageType(MessageType.DELETE)
                            .chatRoomId(chatRoomId)
                            .memberId(memberId)
                            .deleteId(in.getDeleteId())
                            .build();
                    chatMessageEventPublisher.publishWsEvent(event);
                }
                default -> log.warn("Unknown/Unhandled messageType: {}", in.getMessageType());
            }
        } catch (Exception e) {
            log.warn("Failed to handle message from session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void sendWsError(WebSocketSession s, String code, String msg) {
        try {
            var err = java.util.Map.of("type", "ERROR", "code", code, "message", msg);
            s.sendMessage(new TextMessage(objectMapper.writeValueAsString(err)));
        } catch (IOException ignore) {
        }
    }

    private void sendWsErrorAndClose(WebSocketSession s, String code, String msg) {
        sendWsError(s, code, msg);
        try {
            s.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignore) {
        }
    }

    // 실시간 메시지 채팅방에 브로드캐스트
    public void broadcastToRoom(Long chatRoomId, String payload) {
        CopyOnWriteArrayList<WebSocketSession> sessions = chatRoomSessions.get(chatRoomId);
        if (sessions == null)
            return;
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(new TextMessage(payload));
                } else {
                    sessions.remove(s); // COW 리스트라 안전
                }
            } catch (IOException e) {
                log.error("Failed to send message", e);
                sessions.remove(s);
            }
        }
        if (sessions.isEmpty())
            chatRoomSessions.remove(chatRoomId);
    }

    // 60초마다 Ping 프레임 전송 (조용한 방 keepalive 목적)
    @Scheduled(fixedDelayString = "60000")
    public void sendProtocolPing() {
        ByteBuffer payload = ByteBuffer.wrap(new byte[] {1});
        PingMessage ping = new PingMessage(payload);

        for (var entry : chatRoomSessions.entrySet()) {
            var sessions = entry.getValue();
            for (WebSocketSession s : sessions) {
                if (!s.isOpen()) {
                    sessions.remove(s);
                    continue;
                }
                try {
                    s.sendMessage(ping);
                } catch (Exception e) { // 보내기 실패 = 사실상 죽은 세션일 확률 높음
                    sessions.remove(s);
                    try { s.close(); } catch (Exception ignore) {}
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long chatRoomId = sessionRoomMap.remove(session);
        if (chatRoomId != null) {
            var sessions = chatRoomSessions.get(chatRoomId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty())
                    chatRoomSessions.remove(chatRoomId);
            }
        }
        // 멤버 세션 해제
        Long memberId = sessionMemberMap.remove(session);
        if (memberId != null) {
            wsSessionRegistry.remove(memberId, session);
            log.info("WS disconnected: {} memberId={} room={}", session.getId(), memberId, chatRoomId);
        } else {
            log.info("WS disconnected: {} (no member mapping) room={}", session.getId(), chatRoomId);
        }
    }
}
