package com.loopone.loopinbe.domain.chat.chatRoom.entity;

import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.loop.loop.entity.Loop;
import com.loopone.loopinbe.global.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class ChatRoom extends BaseEntity {
    public static final Long ROOM_MEMBER_LIMIT = 100L;

    @Column(length = 20)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatRoomMember> chatRoomMembers = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loop_id")
    private Loop loop;

    @Column(nullable = false)
    @Builder.Default
    private boolean isBotRoom = true;

    private Long teamId;

    private Instant lastMessageAt;

    public void selectLoop(Loop loop) {
        this.loop = loop;
    }

    public void updateTitle(String title) {
        this.title = title;
    }
}
