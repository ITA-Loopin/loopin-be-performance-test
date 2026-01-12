package com.loopone.loopinbe.domain.chat.chatRoom.repository;

import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoom;
import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
    Optional<ChatRoom> findByTeamId(Long teamId);

    // 현재 사용자가 otherMember와 이미 1대1 채팅방이 존재하는지 확인
    @Query("SELECT EXISTS (" +
            "    SELECT 1 FROM ChatRoom c " +
            "    JOIN ChatRoomMember m1 ON c.id = m1.chatRoom.id " +
            "    JOIN ChatRoomMember m2 ON c.id = m2.chatRoom.id " +
            "    WHERE m1.member.id = :memberId1 " +
            "    AND m2.member.id = :memberId2 " +
            "    AND c.id IN (" +
            "        SELECT cm.chatRoom.id FROM ChatRoomMember cm " +
            "        GROUP BY cm.chatRoom.id " +
            "        HAVING COUNT(cm.chatRoom.id) = 2" +
            "    )" +
            ")")
    boolean existsOneOnOneChatRoom(@Param("memberId1") Long memberId1, @Param("memberId2") Long memberId2);

    // 해당 채팅방의 참여자 리스트 가져오기
    @Query("SELECT crm FROM ChatRoomMember crm JOIN FETCH crm.member WHERE crm.chatRoom.id = :chatRoomId")
    List<ChatRoomMember> findChatRoomMembersWithMember(@Param("chatRoomId") Long chatRoomId);

    // 기존 findById 대체용 (채팅방 + 참여 멤버 + 멤버 정보까지 한번에)
    @Query("""
        SELECT cr
        FROM ChatRoom cr
        JOIN FETCH cr.chatRoomMembers crm
        JOIN FETCH crm.member
        WHERE cr.id = :chatRoomId
    """)
    Optional<ChatRoom> findByIdWithMembers(@Param("chatRoomId") Long chatRoomId);

    // 멤버가 참여중인 모든 채팅방 조회 (N+1 방지)
    @Query("""
        SELECT DISTINCT cr
        FROM ChatRoom cr
        JOIN FETCH cr.chatRoomMembers crm
        JOIN FETCH crm.member m
        WHERE m.id = :memberId
    """)
    List<ChatRoom> findByMemberId(@Param("memberId") Long memberId);

    // 해당 채팅방의 참여자id 리스트 가져오기
    @Query("SELECT crm.member.id FROM ChatRoomMember crm WHERE crm.chatRoom.id = :chatRoomId")
    List<Long> findParticipantMemberIds(@Param("chatRoomId") Long chatRoomId);

    // 참여자 권한 검증용 쿼리
    @Query("""
        SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END
        FROM ChatRoomMember m
        WHERE m.chatRoom.id = :chatRoomId AND m.member.id = :memberId
    """)
    boolean existsMember(@Param("chatRoomId") Long chatRoomId, @Param("memberId") Long memberId);

    // AI 채팅방 여부 검증
    @Query("select cr.isBotRoom from ChatRoom cr where cr.id = :chatRoomId")
    Boolean findIsBotRoom(@Param("chatRoomId") Long chatRoomId);

    // lastMessageAt을 더 최신인 경우에만 갱신 (역순 도착 방지)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ChatRoom c
           set c.lastMessageAt = :messageAt
         where c.id = :chatRoomId
           and (c.lastMessageAt is null or c.lastMessageAt < :messageAt)
    """)
    int updateLastMessageAtIfNewer(@Param("chatRoomId") Long chatRoomId, @Param("messageAt") Instant messageAt);

    // ChatRoomConverter에서 필요 (N+1 방지)
    @Query("""
    select crm
    from ChatRoomMember crm
    join fetch crm.chatRoom cr
    join fetch cr.member owner
    left join fetch cr.loop
    where crm.member.id = :memberId
    order by cr.lastMessageAt desc
""")
    List<ChatRoomMember> findMyChatRooms(@Param("memberId") Long memberId);

    @Query("""
    select crm
    from ChatRoomMember crm
    join fetch crm.chatRoom cr
    join fetch crm.member owner
    left join fetch cr.loop
    where crm.member.id = :memberId
      and cr.isBotRoom = true
    order by cr.lastMessageAt desc
""")
    List<ChatRoomMember> findAiChatRooms(@Param("memberId") Long memberId);

    @Query("""
    select crm
    from ChatRoomMember crm
    join fetch crm.chatRoom cr
    join fetch crm.member owner
    left join fetch cr.loop
    where crm.member.id = :memberId
      and (cr.isBotRoom = false or cr.isBotRoom is null)
    order by cr.lastMessageAt desc
""")
    List<ChatRoomMember> findTeamChatRooms(@Param("memberId") Long memberId);

    @Query("SELECT DISTINCT cr FROM ChatRoom cr " +
           "LEFT JOIN FETCH cr.loop l " +
           "LEFT JOIN FETCH l.loopChecklists " +
           "LEFT JOIN FETCH l.loopRule lr " +
           "WHERE cr.id = :chatRoomId")
    Optional<ChatRoom> findByIdWithLoopAndChecklists(@Param("chatRoomId") Long chatRoomId);
}
