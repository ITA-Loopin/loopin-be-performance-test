package com.loopone.loopinbe.global.initData.member.service;

import com.loopone.loopinbe.domain.account.member.dto.req.MemberCreateRequest;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.service.MemberService;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotProdMemberService {
    private final MemberService memberService;
    private final ChatRoomService chatRoomService;

    // 유저 1 ~ 5 생성
    @Transactional
    public void createMembers(List<String> memberEmails) {
        List<String> nicknames = List.of("seoul", "incheon", "gangneung", "busan", "jeju");
        for (int i = 0; i < nicknames.size(); i++){
            MemberCreateRequest memberCreateRequest = MemberCreateRequest.builder()
                    .nickname(nicknames.get(i))
                    .email("user" + (i + 1) + "@example.com")
                    .build();
            Member member = memberService.regularSignUp(memberCreateRequest);
            chatRoomService.createAiChatRoom(member.getId());
            memberEmails.add(member.getEmail());
        }
    }

    // 유저 1 ~ 1000 생성
    @Transactional
    public void createTestMembers(List<String> memberEmails) {
        for (int n = 1; n <= 1000; n++) {
            String nickname = "user" + n; // user1 ... user1000
            String email = "user" + n + "@example.com";
            MemberCreateRequest req = MemberCreateRequest.builder()
                    .nickname(nickname)
                    .email(email)
                    .build();
            Member member = memberService.regularSignUp(req);
            chatRoomService.createAiChatRoom(member.getId());
            memberEmails.add(member.getEmail());
        }
    }
}
