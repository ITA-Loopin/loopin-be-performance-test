package com.loopone.loopinbe.domain.chat.chatMessage.repositoryImpl;

import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatAttachment;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.ChatMessage;
import com.loopone.loopinbe.domain.chat.chatMessage.repository.ChatMessageMongoRepositoryCustom;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChatMessageMongoRepositoryImpl implements ChatMessageMongoRepositoryCustom {
    private final MongoTemplate mongoTemplate;

    // 채팅 내용 저장
    @Override
    public ChatMessage upsertInbound(
            String id,
            String clientMessageId,
            Long chatRoomId,
            Long memberId,
            String content,
            List<ChatAttachment> attachments,
            List<LoopCreateRequest> recommendations,
            ChatMessage.AuthorType authorType,
            Instant createdAt,
            Instant modifiedAt
    ) {
        Query q = new Query(Criteria.where("_id").is(id));
        Update u = new Update()
                // 멱등: 최초 삽입시에만 고정되는 필드
                .setOnInsert("_id", id)
                .setOnInsert("clientMessageId", clientMessageId)
                .setOnInsert("chatRoomId", chatRoomId)
                .setOnInsert("memberId", memberId)
                .setOnInsert("content", content)
                .setOnInsert("attachments", attachments)
                .setOnInsert("recommendations", recommendations)
                .setOnInsert("authorType", authorType)
                .setOnInsert("createdAt", createdAt)
                .setOnInsert("modifiedAt", modifiedAt);
        FindAndModifyOptions opt = FindAndModifyOptions.options()
                .upsert(true)
                .returnNew(true);
        return mongoTemplate.findAndModify(q, u, opt, ChatMessage.class);
    }

    // 채팅방 내 내용 검색 (Mongo 텍스트 인덱스 사용)
    @Override
    public Page<ChatMessage> searchByKeyword(Long chatRoomId, String keyword, Pageable pageable) {
        // $text 검색 + chatRoomId 필터
        TextCriteria text = TextCriteria.forDefaultLanguage().matching(keyword);

        Query q = TextQuery.queryText(text)
                .addCriteria(Criteria.where("chatRoomId").is(chatRoomId))
                .with(pageable);
        // 정렬이 없다면 기본 최신순
        if (!pageable.getSort().isSorted()) {
            q.with(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        List<ChatMessage> results = mongoTemplate.find(q, ChatMessage.class);

        // count는 textQuery에서 pageable을 제거하고 수행
        Query countQuery = TextQuery.queryText(text)
                .addCriteria(Criteria.where("chatRoomId").is(chatRoomId));
        long total = mongoTemplate.count(countQuery, ChatMessage.class);
        return new PageImpl<>(results, pageable, total);
    }
}
