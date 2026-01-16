package com.loopone.loopinbe.domain.chat.chatMessage.serviceImpl;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.chat.chatMessage.converter.ChatMessageConverter;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatAttachment;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatMessagePayload;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.MessageContext;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.req.ChatMessageRequest;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.res.ChatMessageResponse;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessagePage;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.type.MessageType;
import com.loopone.loopinbe.domain.chat.chatMessage.repository.ChatMessageMongoRepository;
import com.loopone.loopinbe.domain.chat.chatMessage.service.ChatMessageService;
import com.loopone.loopinbe.domain.chat.chatMessage.validator.AttachmentValidator;
import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoom;
import com.loopone.loopinbe.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomStateService;
import com.loopone.loopinbe.domain.loop.ai.dto.AiPayload;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopDetailResponse;
import com.loopone.loopinbe.domain.loop.loop.entity.Loop;
import com.loopone.loopinbe.domain.loop.loop.mapper.LoopMapper;
import com.loopone.loopinbe.domain.sse.service.SseEmitterService;
import com.loopone.loopinbe.global.common.response.PageResponse;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import com.loopone.loopinbe.global.kafka.event.ai.AiEventPublisher;
import com.loopone.loopinbe.global.kafka.event.chatMessage.ChatMessageEventPublisher;
import com.loopone.loopinbe.global.s3.S3Service;
import com.loopone.loopinbe.global.webSocket.payload.ChatWebSocketPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

import static com.loopone.loopinbe.domain.chat.chatMessage.entity.type.MessageType.*;
import static com.loopone.loopinbe.global.constants.Constant.*;
import static com.loopone.loopinbe.global.constants.KafkaKey.OPEN_AI_CREATE_TOPIC;
import static com.loopone.loopinbe.global.constants.KafkaKey.OPEN_AI_UPDATE_TOPIC;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageMongoRepository chatMessageMongoRepository;
    private final ChatMessageConverter chatMessageConverter;
    private final AiEventPublisher aiEventPublisher;
    private final LoopMapper loopMapper;
    private final SseEmitterService sseEmitterService;
    private final S3Service s3Service;
    private final ChatMessageEventPublisher chatMessageEventPublisher;
    private final ChatRoomStateService chatRoomStateService;

    // 채팅방 과거 메시지 조회 [참여자 권한]
    @Override
    @Transactional
    public PageResponse<ChatMessageResponse> findByChatRoomId(
            Long chatRoomId, Pageable pageable, CurrentUserDto currentUser
    ) {
        try {
            checkPageSize(pageable.getPageSize());
            // 참여자 검증
            boolean memberExists = chatRoomRepository.existsMember(chatRoomId, currentUser.id());
            if (!memberExists) throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
            Pageable sortedPageable = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );
            Page<ChatMessage> page = chatMessageMongoRepository.findByChatRoomId(chatRoomId, sortedPageable);

            Map<Long, Member> memberMap = chatMessageConverter.loadMembers(page.getContent());
            return PageResponse.of(
                    page.map(cm -> chatMessageConverter.toChatMessageResponse(cm, memberMap))
            );
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in findByChatRoomId - chatRoomId: {}, loginUserId: {}, error: {}",
                    chatRoomId, currentUser.id(), e.getMessage(), e);
            throw new ServiceException(ReturnCode.INTERNAL_SERVER_ERROR);
        }
    }

    // 채팅방 메시지 검색(내용) [참여자 권한]
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> searchByKeyword(
            Long chatRoomId, String keyword, Pageable pageable, CurrentUserDto currentUser
    ) {
        checkPageSize(pageable.getPageSize());
        // 참여자 검증
        boolean memberExists = chatRoomRepository.existsMember(chatRoomId, currentUser.id());
        if (!memberExists) throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<ChatMessage> page = chatMessageMongoRepository.searchByKeyword(chatRoomId, keyword, sortedPageable);

        Map<Long, Member> memberMap = chatMessageConverter.loadMembers(page.getContent());
        return PageResponse.of(
                page.map(cm -> chatMessageConverter.toChatMessageResponse(cm, memberMap))
        );
    }

    // Kafka 인바운드 메시지 처리(권한검증 + 멱등 저장 + Mongo 업서트)
    @Override
    @Transactional
    public ChatMessagePayload processInbound(ChatMessagePayload in) {
        log.info("Mongo 단일 저장 처리 시작: id={}", in.id());
        // 1) 권한 검증 - WebSocketHandler / EventConsumer / sendChatMessage() 등 앞 단에서 검증
        // 2) AI 채팅방 여부 검증
        Boolean isBotRoom = chatRoomRepository.findIsBotRoom(in.chatRoomId());
        if (isBotRoom == null) throw new ServiceException(ReturnCode.CHATROOM_NOT_FOUND);
        // 3) Mongo Upsert (멱등)
        ChatMessage saved = chatMessageMongoRepository.upsertInbound(
                in.id(),
                in.clientMessageId().toString(),
                in.chatRoomId(),
                in.memberId(),
                in.content(),
                in.attachments(),
                in.recommendations(),
                in.loopRuleId(),
                in.deleteMessageId(),
                in.authorType(),
                in.createdAt(),
                in.modifiedAt()
        );
        log.info("Mongo 단일 저장 완료: id={}", in.id());
        chatRoomRepository.updateLastMessageAtIfNewer(saved.getChatRoomId(), saved.getCreatedAt());
        return new ChatMessagePayload(
                saved.getId(),
                saved.getClientMessageId(),
                saved.getChatRoomId(),
                in.memberId(),
                saved.getContent(),
                saved.getAttachments(),
                saved.getRecommendations(),
                saved.getLoopRuleId(),
                saved.getDeleteMessageId(),
                saved.getAuthorType(),
                isBotRoom,
                saved.getCreatedAt(),
                saved.getModifiedAt()
        );
    }

    @Override
    @Transactional
    public void deleteChatMessage(String messageId, Long memberId) {
        ChatMessage message = chatMessageMongoRepository.findById(messageId)
                .orElseThrow(() -> new ServiceException(ReturnCode.CHATMESSAGE_NOT_FOUND));

        if (!message.getMemberId().equals(memberId)) {
            throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        }

        chatMessageMongoRepository.delete(message);
    }

    // 채팅방의 모든 메시지 삭제
    @Override
    @Transactional
    public void deleteAllChatMessages(Long chatRoomId) {
        chatMessageMongoRepository.deleteByChatRoomId(chatRoomId);
    }

    // AI 채팅방 메시지 전송
    @Override
    @Transactional
    public void sendChatMessage(Long chatRoomId, ChatMessageRequest request, CurrentUserDto currentUser) {

        validateParticipant(chatRoomId, currentUser.id());
        validateMessageType(request.messageType());

        ChatRoom chatRoom = chatRoomRepository.findByIdWithLoopAndChecklists(chatRoomId)
                .orElseThrow(() -> new ServiceException(ReturnCode.CHATROOM_NOT_FOUND));

        Loop loop = chatRoom.getLoop();
        initializeLoopRule(loop);

        LoopDetailResponse loopDetailResponse =
                (loop != null) ? loopMapper.toDetailResponse(loop) : null;

        MessageContext ctx = switch (request.messageType()) {
            case START_CHATROOM -> handleStartChatRoom(chatRoomId);
            case GET_LOOP -> handleGetLoop(loop, loopDetailResponse, chatRoomId);
            case BEFORE_UDATE_LOOP -> handleBeforeUpdateLoop(loop, loopDetailResponse, chatRoomId);
            case RECREATE_LOOP -> handleRecreateLoop(chatRoomId);
            default -> MessageContext.builder()
                    .msgId(request.clientMessageId())
                    .content(request.content())
                    .author(ChatMessage.AuthorType.USER)
                    .build();
        };

        ChatMessagePayload payload = toChatMessagePayload(
                ctx.msgId(),
                chatRoomId,
                currentUser.id(),
                ctx.content(),
                null,
                ctx.recommendations(),
                ctx.deleteMessageId(),
                ctx.author(),
                ctx.loopRuleId()
        );

        ChatMessagePayload saved = processInbound(payload);

        Map<Long, Member> memberMap = chatMessageConverter.loadMembersFromPayload(List.of(saved));
        ChatMessageResponse response = chatMessageConverter.toChatMessageResponse(saved, memberMap);
        sseEmitterService.sendToClient(chatRoomId, MESSAGE, response);

        publishAiIfNeeded(chatRoom.getId(), request.messageType(), saved, loopDetailResponse);
    }

    // 해당 채팅방에서 파일 메시지 전송 [참여자 권한]
    @Override
    @Transactional
    public void sendAttachment(Long chatRoomId, UUID clientMessageId, List<MultipartFile> images, List<MultipartFile> files, CurrentUserDto currentUser) {
        // 참여자 검증
        boolean memberExists = chatRoomRepository.existsMember(chatRoomId, currentUser.id());
        if (!memberExists) throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        // 파일 검증
        AttachmentValidator.validateImages(images);
        AttachmentValidator.validateFiles(files);
        boolean hasAny = (images != null && images.stream().anyMatch(f -> f != null && !f.isEmpty()))
                || (files != null && files.stream().anyMatch(f -> f != null && !f.isEmpty()));
        if (!hasAny) throw new ServiceException(ReturnCode.FILE_UPLOAD_ERROR);
        // 3) S3 업로드
        List<ChatAttachment> uploaded = new ArrayList<>();
        try {
            // images
            if (images != null) {
                for (MultipartFile img : images) {
                    if (img == null || img.isEmpty()) continue;
                    uploaded.add(s3Service.uploadChatImage(img, "chat-images"));
                }
            }
            // files
            if (files != null) {
                for (MultipartFile f : files) {
                    if (f == null || f.isEmpty()) continue;
                    uploaded.add(s3Service.uploadChatFile(f, "chat-files"));
                }
            }
            if (uploaded.isEmpty()) throw new ServiceException(ReturnCode.FILE_UPLOAD_ERROR);
            // 4) Payload 생성/저장
            ChatMessagePayload payload = toChatMessagePayload(
                    clientMessageId,
                    chatRoomId,
                    currentUser.id(),
                    null,
                    uploaded,
                    null,
                    null,
                    ChatMessage.AuthorType.USER,
                    null
            );
            ChatMessagePayload saved = processInbound(payload);
            // 5) Response 변환
            Map<Long, Member> memberMap = chatMessageConverter.loadMembersFromPayload(List.of(saved));
            ChatMessageResponse response = chatMessageConverter.toChatMessageResponse(saved, memberMap);
            // 6) 이벤트 발행
            publishAttachmentMessage(chatRoomId, saved.clientMessageId(), response);
        } catch (RuntimeException | IOException e) {
            // S3 업로드는 트랜잭션 롤백이 안 되므로 보상 삭제
            List<String> keys = uploaded.stream().map(ChatAttachment::key).toList();
            s3Service.deleteAllByKeys(keys);
            if (e instanceof ServiceException se) throw se;
            throw new ServiceException(ReturnCode.INTERNAL_ERROR);
        }
    }

    @Override
    @Transactional
    public String deleteRecommendationMessage(Long chatRoomId) {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChatMessage> recentMessages = chatMessageMongoRepository.findByChatRoomId(chatRoomId, pageable);

        Optional<ChatMessage> target = recentMessages.getContent().stream()
                .filter(msg -> msg.getAuthorType() == ChatMessage.AuthorType.BOT
                        && msg.getRecommendations() != null
                        && !msg.getRecommendations().isEmpty())
                .findFirst();

        if (target.isPresent()) {
            ChatMessage msg = target.get();
            String id = msg.getId();
            chatMessageMongoRepository.delete(msg);
            return id;
        }
        return null;
    }

    // ----------------- 헬퍼 메서드 -----------------

    private LoopCreateRequest convertToCreateRequest(LoopDetailResponse detail, Long chatRoomId) {
        // LoopDetailResponse -> LoopCreateRequest 변환
        // 주의: ID나 진행률 정보는 유실됨
        List<String> checklists = (detail.checklists() != null)
                ? detail.checklists().stream().map(com.loopone.loopinbe.domain.loop.loopChecklist.dto.res.LoopChecklistResponse::content).toList()
                : Collections.emptyList();

        var rule = detail.loopRule();

        return new LoopCreateRequest(
                detail.title(),
                detail.content(),
                (rule != null) ? rule.scheduleType() : com.loopone.loopinbe.domain.loop.loop.enums.RepeatType.NONE,
                (rule == null) ? detail.loopDate() : null, // 규칙 없으면 단일 날짜 사용
                (rule != null) ? rule.daysOfWeek() : null,
                (rule != null) ? rule.startDate() : null,
                (rule != null) ? rule.endDate() : null,
                checklists,
                chatRoomId
        );
    }

    // 요청 페이지 수 제한
    private void checkPageSize(int pageSize) {
        int maxPageSize = ChatMessagePage.getMaxPageSize();
        if (pageSize > maxPageSize) {
            throw new ServiceException(ReturnCode.PAGE_REQUEST_FAIL);
        }
    }

    private ChatMessagePayload toChatMessagePayload(UUID clientMessageId, Long chatRoomId, Long userId, String content, List<ChatAttachment> attachments, List<LoopCreateRequest> recommendations, String deleteMessageId, ChatMessage.AuthorType authorType, Long loopRuleId) {
        String id;
        if (authorType.equals(ChatMessage.AuthorType.BOT)) {
            id = "ai:" + clientMessageId;
        } else {
            id = "u:" + clientMessageId;
        }
        return new ChatMessagePayload(
                id,
                clientMessageId,
                chatRoomId,
                userId,
                content,
                attachments,
                recommendations,
                loopRuleId,
                deleteMessageId,
                authorType,
                true,
                java.time.Instant.now(),
                java.time.Instant.now());
    }

    private void publishAI(ChatMessagePayload saved, LoopDetailResponse loopDetailResponse, String topic) {
        AiPayload req = new AiPayload(
                saved.clientMessageId(),
                saved.chatRoomId(),
                saved.id(),
                saved.memberId(),
                saved.content(),
                loopDetailResponse,
                java.time.Instant.now());
        aiEventPublisher.publishAiRequest(req, topic);
    }

    private void publishAttachmentMessage(Long chatRoomId, UUID clientMessageId, ChatMessageResponse response) {
        ChatWebSocketPayload out = ChatWebSocketPayload.builder()
                .messageType(MessageType.MESSAGE)
                .chatRoomId(chatRoomId)
                .clientMessageId(clientMessageId)
                .chatMessageResponse(response)
                .build();
        chatMessageEventPublisher.publishWsEvent(out);
    }

    private MessageContext handleStartChatRoom(Long chatRoomId) {
        return MessageContext.builder()
                .msgId(UUID.randomUUID())
                .content(AI_START_MESSAGE)
                .author(ChatMessage.AuthorType.BOT)
                .build();
    }

    private MessageContext handleGetLoop(Loop loop, LoopDetailResponse loopDetailResponse, Long chatRoomId) {
        return MessageContext.builder()
                .msgId(UUID.randomUUID())
                .content(AI_AFTER_SELECT_LOOP_MESSAGE)
                .author(ChatMessage.AuthorType.BOT)
                .recommendations(loopDetailResponse != null
                        ? List.of(convertToCreateRequest(loopDetailResponse, chatRoomId))
                        : null)
                .loopRuleId(loop != null && loop.getLoopRule() != null
                        ? loop.getLoopRule().getId()
                        : null)
                .build();
    }

    private MessageContext handleBeforeUpdateLoop(Loop loop, LoopDetailResponse loopDetailResponse, Long chatRoomId) {
        return MessageContext.builder()
                .msgId(UUID.randomUUID())
                .content(AI_UPDATE_MESSAGE)
                .author(ChatMessage.AuthorType.BOT)
                .recommendations(loopDetailResponse != null
                        ? List.of(convertToCreateRequest(loopDetailResponse, chatRoomId))
                        : null)
                .loopRuleId(loop != null && loop.getLoopRule() != null
                        ? loop.getLoopRule().getId()
                        : null)
                .build();
    }

    private MessageContext handleRecreateLoop(Long chatRoomId) {
        return MessageContext.builder()
                .msgId(UUID.randomUUID())
                .content(AI_RECREATE_MESSAGE)
                .author(ChatMessage.AuthorType.BOT)
                .deleteMessageId(deleteRecommendationMessage(chatRoomId))
                .build();
    }

    private void validateParticipant(Long chatRoomId, Long memberId) {
        if (!chatRoomRepository.existsMember(chatRoomId, memberId)) {
            throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        }
    }

    private void validateMessageType(MessageType type) {
        if (!EnumSet.of(START_CHATROOM, CREATE_LOOP, UPDATE_LOOP, GET_LOOP, RECREATE_LOOP, BEFORE_UDATE_LOOP).contains(type)) {
            throw new ServiceException(ReturnCode.CHATMESSAGE_INVALID_TYPE);
        }
    }

    private void initializeLoopRule(Loop loop) {
        if (loop != null && loop.getLoopRule() != null) {
            Hibernate.initialize(loop.getLoopRule().getDaysOfWeek());
        }
    }

    private void publishAiIfNeeded(Long chatRoomId, MessageType type, ChatMessagePayload saved, LoopDetailResponse loopDetailResponse) {
        if (type == CREATE_LOOP) {
            publishAI(saved, null, OPEN_AI_CREATE_TOPIC);
        } else if (type == UPDATE_LOOP) {
            publishAI(saved, loopDetailResponse, OPEN_AI_UPDATE_TOPIC);
            chatRoomStateService.setCallUpdateLoop(chatRoomId, true);
        }
    }
}