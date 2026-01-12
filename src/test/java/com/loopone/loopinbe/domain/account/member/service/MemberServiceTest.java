package com.loopone.loopinbe.domain.account.member.service;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.auth.dto.AuthPayload;
import com.loopone.loopinbe.domain.account.member.converter.MemberConverterImpl;
import com.loopone.loopinbe.domain.account.member.converter.SimpleMemberConverterImpl;
import com.loopone.loopinbe.domain.account.member.dto.req.MemberCreateRequest;
import com.loopone.loopinbe.domain.account.member.dto.req.MemberUpdateRequest;
import com.loopone.loopinbe.domain.account.member.dto.res.MemberResponse;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.entity.MemberFollow;
import com.loopone.loopinbe.domain.account.member.entity.MemberFollowReq;
import com.loopone.loopinbe.domain.account.member.entity.MemberPage;
import com.loopone.loopinbe.domain.account.member.enums.ProfileImageState;
import com.loopone.loopinbe.domain.account.member.repository.MemberFollowRepository;
import com.loopone.loopinbe.domain.account.member.repository.MemberFollowReqRepository;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.account.member.serviceImpl.MemberServiceImpl;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.ChatAttachment;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomService;
import com.loopone.loopinbe.domain.loop.loop.service.LoopService;
import com.loopone.loopinbe.domain.notification.dto.NotificationPayload;
import com.loopone.loopinbe.domain.team.team.service.TeamService;
import com.loopone.loopinbe.global.common.response.PageResponse;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import com.loopone.loopinbe.global.kafka.event.auth.AuthEventPublisher;
import com.loopone.loopinbe.global.kafka.event.notification.NotificationEventPublisher;
import com.loopone.loopinbe.global.s3.S3Service;
import com.loopone.loopinbe.support.TestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mockStatic;

@DataJpaTest
@ActiveProfiles("test")
@Import({TestContainersConfig.class, MemberServiceImpl.class, MemberConverterImpl.class, SimpleMemberConverterImpl.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MemberServiceTest {
    // ===== Real Repositories =====
    @Autowired
    MemberRepository memberRepository;
    @Autowired
    MemberFollowReqRepository memberFollowReqRepository;
    @Autowired
    MemberFollowRepository memberFollowRepository;

    // ===== SUT =====
    @Autowired MemberServiceImpl memberService;

    // ===== External boundaries (mock) =====
    @MockitoBean S3Service s3Service;
    @MockitoBean ChatRoomService chatRoomService;
    @MockitoBean TeamService teamService;
    @MockitoBean LoopService loopService;
    @MockitoBean NotificationEventPublisher notificationEventPublisher;
    @MockitoBean AuthEventPublisher authEventPublisher;

    @AfterEach
    void cleanup() {
        // 순서 중요(관계 테이블 먼저)
        memberFollowRepository.deleteAll();
        memberFollowReqRepository.deleteAll();
        memberRepository.deleteAll();
    }

    // ===== Helpers =====
    private Member persistMember(String email, String nickname, String profileUrl) {
        Member m = Member.builder()
                .email(email)
                .nickname(nickname)
                .profileImageUrl(profileUrl)
                .state(Member.State.NORMAL)
                .role(Member.MemberRole.ROLE_USER)
                .oAuthProvider(Member.OAuthProvider.GOOGLE)
                .providerId("pid")
                .build();
        return memberRepository.saveAndFlush(m);
    }

    private CurrentUserDto cu(Member m) {
        return new CurrentUserDto(
                m.getId(),
                m.getEmail(),
                null,
                m.getNickname(),
                null,
                null,
                null,
                m.getProfileImageUrl(),
                m.getState(),
                m.getRole(),
                m.getOAuthProvider(),
                m.getProviderId()
        );
    }

    // =========================================================
    // regularSignUp
    // =========================================================
    @Nested
    class RegularSignUp {

        @Test
        @DisplayName("성공: 저장되고 필드가 채워진다")
        void success() {
            var req = new MemberCreateRequest("new@loop.in", "newNick", Member.OAuthProvider.GOOGLE, "pid-x");

            var saved = memberService.regularSignUp(req);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getEmail()).isEqualTo("new@loop.in");
            assertThat(saved.getNickname()).isEqualTo("newNick");
            assertThat(saved.getOAuthProvider()).isEqualTo(Member.OAuthProvider.GOOGLE);
            assertThat(saved.getProviderId()).isEqualTo("pid-x");

            assertThat(memberRepository.findById(saved.getId())).isPresent();
        }

        @Test
        @DisplayName("이메일 중복 -> EMAIL_ALREADY_USED")
        void emailDuplicate() {
            persistMember("dup@loop.in", "a", null);

            var req = new MemberCreateRequest("dup@loop.in", "b", Member.OAuthProvider.GOOGLE, "pid");

            assertThatThrownBy(() -> memberService.regularSignUp(req))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.EMAIL_ALREADY_USED);
        }

        @Test
        @DisplayName("닉네임 중복 -> NICKNAME_ALREADY_USED")
        void nicknameDuplicate() {
            persistMember("a@loop.in", "dupNick", null);

            var req = new MemberCreateRequest("b@loop.in", "dupNick", Member.OAuthProvider.GOOGLE, "pid");

            assertThatThrownBy(() -> memberService.regularSignUp(req))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.NICKNAME_ALREADY_USED);
        }
    }

    // =========================================================
    // getMyInfo / getMyDetailInfo
    // =========================================================
    @Nested
    class GetMyInfo {

        @Test
        @DisplayName("내 정보 조회: 없으면 USER_NOT_FOUND")
        void notFound() {
            var fake = new CurrentUserDto(
                    999L, "x@x", null, "x", null, null, null,
                    null, Member.State.NORMAL, Member.MemberRole.ROLE_USER, Member.OAuthProvider.GOOGLE, "pid"
            );

            assertThatThrownBy(() -> memberService.getMyInfo(fake))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("내 정보 조회: 성공(Converter 결과 반환)")
        void success() {
            var me = persistMember("me@loop.in", "me", "http://img");
            var dto = cu(me);
            var result = memberService.getMyInfo(dto);

            assertThat(result.getId()).isEqualTo(me.getId());
            assertThat(result.getEmail()).isEqualTo(me.getEmail());
            assertThat(result.getNickname()).isEqualTo("me");
            assertThat(result.getProfileImageUrl()).isEqualTo(me.getProfileImageUrl());
        }

        @Test
        @DisplayName("내 상세조회: 성공(Converter 결과 반환)")
        void detailSuccess() {
            var me = persistMember("me@loop.in", "me", null);
            var dto = cu(me);
            var result = memberService.getMyDetailInfo(dto);

            assertThat(result.getId()).isEqualTo(me.getId());
            assertThat(result.getEmail()).isEqualTo(me.getEmail());
            assertThat(result.getNickname()).isEqualTo(me.getNickname());
            assertThat(result.getProfileImageUrl()).isEqualTo(me.getProfileImageUrl());
            assertThat(result.getFollowMemberCount()).isEqualTo(0L);
            assertThat(result.getFollowedMemberCount()).isEqualTo(0L);
            assertThat(result.getFollowList()).isNullOrEmpty();
            assertThat(result.getFollowedList()).isNullOrEmpty();
            assertThat(result.getFollowReqList()).isNullOrEmpty();
            assertThat(result.getFollowRecList()).isNullOrEmpty();
        }
    }

    // =========================================================
    // getMemberInfo / getDetailMemberInfo (타인 조회)
    // =========================================================
    @Nested
    class GetMemberInfo {

        @Test
        @DisplayName("타인 정보 조회(getMemberInfo): 없으면 USER_NOT_FOUND")
        void memberInfo_notFound() {
            assertThatThrownBy(() -> memberService.getMemberInfo(99999L))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("타인 정보 조회(getMemberInfo): 성공(Converter 결과 반환)")
        void memberInfo_success() {
            var you = persistMember("you@loop.in", "you", "http://you-img");
            var result = memberService.getMemberInfo(you.getId());

            assertThat(result.getId()).isEqualTo(you.getId());
            assertThat(result.getEmail()).isEqualTo(you.getEmail());
            assertThat(result.getNickname()).isEqualTo("you");
            assertThat(result.getProfileImageUrl()).isEqualTo("http://you-img");
        }

        @Test
        @DisplayName("타인 상세조회(getDetailMemberInfo): 없으면 USER_NOT_FOUND")
        void detailMemberInfo_notFound() {
            assertThatThrownBy(() -> memberService.getDetailMemberInfo(99999L))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("타인 상세조회(getDetailMemberInfo): 성공(Converter 결과 반환)")
        void detailMemberInfo_success() {
            var you = persistMember("you2@loop.in", "you2", null);
            var result = memberService.getDetailMemberInfo(you.getId());

            assertThat(result.getId()).isEqualTo(you.getId());
            assertThat(result.getEmail()).isEqualTo(you.getEmail());
            assertThat(result.getNickname()).isEqualTo(you.getNickname());
            assertThat(result.getProfileImageUrl()).isEqualTo(you.getProfileImageUrl());
            assertThat(result.getFollowMemberCount()).isEqualTo(0L);
            assertThat(result.getFollowedMemberCount()).isEqualTo(0L);
            assertThat(result.getFollowList()).isNullOrEmpty();
            assertThat(result.getFollowedList()).isNullOrEmpty();
            assertThat(result.getFollowReqList()).isNullOrEmpty();
            assertThat(result.getFollowRecList()).isNullOrEmpty();
        }
    }

    // =========================================================
    // checkNickname
    // =========================================================
    @Nested
    class CheckNickname {

        @Test
        @DisplayName("중복이면 NICKNAME_ALREADY_USED")
        void duplicate() {
            persistMember("a@a", "dup", null);

            assertThatThrownBy(() -> memberService.checkNickname("dup"))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.NICKNAME_ALREADY_USED);
        }

        @Test
        @DisplayName("중복 아니면 통과")
        void ok() {
            assertThatCode(() -> memberService.checkNickname("ok")).doesNotThrowAnyException();
        }
    }

    // =========================================================
    // updateMember (ProfileImageState 기반)
    // =========================================================
    @Nested
    class UpdateMember {
        private static final String OLD_URL = "https://cdn.loop.in/profile-images/old.png";
        private static final String OLD_KEY = "profile-images/old.png";
        private static final String NEW_KEY = "profile-images/new.png";
        private static final String NEW_URL = "http://new";

        @Test
        @DisplayName("닉네임 중복이면 NICKNAME_ALREADY_USED")
        void nicknameDuplicate() {
            var me = persistMember("me@loop.in", "me", OLD_URL);
            persistMember("x@loop.in", "dup", null);

            var dto = cu(me);
            var req = new MemberUpdateRequest("dup", ProfileImageState.MAINTAIN);

            assertThatThrownBy(() -> memberService.updateMember(req, null, dto))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.NICKNAME_ALREADY_USED);
        }

        @Test
        @DisplayName("MAINTAIN: 프로필 이미지 유지(업로드 파일이 와도 무시)")
        void maintain() {
            var me = persistMember("me@loop.in", "me", OLD_URL);
            var dto = cu(me);

            var file = new MockMultipartFile(
                    "image", "a.png", "image/png",
                    "dummy".getBytes(StandardCharsets.UTF_8)
            );

            var req = new MemberUpdateRequest("me2", ProfileImageState.MAINTAIN);

            memberService.updateMember(req, file, dto);

            var reloaded = memberRepository.findById(me.getId()).orElseThrow();
            assertThat(reloaded.getNickname()).isEqualTo("me2");
            assertThat(reloaded.getProfileImageUrl()).isEqualTo(OLD_URL);
            then(s3Service).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("UPDATE: 기존 이미지 삭제 후 새 이미지 업로드")
        void update() throws Exception {
            var me = persistMember("me@loop.in", "me", OLD_URL);
            var dto = cu(me);

            // uploadChatImage -> ChatAttachment(key)
            ChatAttachment uploaded = mock(ChatAttachment.class);
            given(uploaded.key()).willReturn(NEW_KEY);
            given(s3Service.uploadChatImage(any(MultipartFile.class), eq("profile-images")))
                    .willReturn(uploaded);
            given(s3Service.toPublicUrl(NEW_KEY))
                    .willReturn(NEW_URL);

            var file = new MockMultipartFile(
                    "image", "a.png", "image/png",
                    "dummy".getBytes(StandardCharsets.UTF_8)
            );

            var req = new MemberUpdateRequest("me2", ProfileImageState.UPDATE);

            memberService.updateMember(req, file, dto);

            var reloaded = memberRepository.findById(me.getId()).orElseThrow();
            assertThat(reloaded.getProfileImageUrl()).isEqualTo("http://new");
            assertThat(reloaded.getNickname()).isEqualTo("me2");

            // 검증: 업로드 -> public url 변환 -> 기존 key 삭제가 호출되었는지
            then(s3Service).should().uploadChatImage(any(MultipartFile.class), eq("profile-images"));
            then(s3Service).should().toPublicUrl(NEW_KEY);
            then(s3Service).should().deleteObjectByKey(OLD_KEY);
        }

        @Test
        @DisplayName("UPDATE: 파일 없으면 PROFILE_IMAGE_REQUIRED")
        void update_requiresFile() {
            var me = persistMember("me@loop.in", "me", OLD_URL);
            var dto = cu(me);

            var req = new MemberUpdateRequest("me2", ProfileImageState.UPDATE);

            assertThatThrownBy(() -> memberService.updateMember(req, null, dto))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.PROFILE_IMAGE_REQUIRED);
        }

        @Test
        @DisplayName("UPDATE: 업로드 중 IOException -> INTERNAL_ERROR")
        void update_ioException() throws Exception {
            var me = persistMember("me@loop.in", "me", OLD_URL);
            var dto = cu(me);

            willThrow(new IOException("boom"))
                    .given(s3Service).uploadChatImage(any(MultipartFile.class), eq("profile-images"));

            var file = new MockMultipartFile(
                    "image", "a.png", "image/png",
                    "dummy".getBytes(StandardCharsets.UTF_8)
            );

            var req = new MemberUpdateRequest("me2", ProfileImageState.UPDATE);

            assertThatThrownBy(() -> memberService.updateMember(req, file, dto))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.INTERNAL_ERROR);

            // 업로드 실패면 이후 단계 호출 X
            then(s3Service).should().uploadChatImage(any(MultipartFile.class), eq("profile-images"));
            then(s3Service).should(never()).toPublicUrl(anyString());
            then(s3Service).should(never()).deleteObjectByKey(anyString());
        }

        @Test
        @DisplayName("DELETE: 기존 이미지 삭제 후 빈 문자열로 세팅")
        void delete() throws IOException {
            var me = persistMember("me@loop.in", "me", OLD_URL);

            var dto = cu(me);

            var req = new MemberUpdateRequest("me2", ProfileImageState.DELETE);

            memberService.updateMember(req, null, dto);

            var reloaded = memberRepository.findById(me.getId()).orElseThrow();
            assertThat(reloaded.getNickname()).isEqualTo("me2");
            assertThat(reloaded.getProfileImageUrl()).isEqualTo("");

            then(s3Service).should().deleteObjectByKey(OLD_KEY);
            then(s3Service).should(never()).uploadChatImage(any(), anyString());
            then(s3Service).should(never()).toPublicUrl(anyString());
        }
    }

    // =========================================================
    // deleteMember: afterCommit 로그아웃 이벤트까지 검증하려면 "커밋" 필요
    // =========================================================
    @Nested
    class DeleteMember {

        @Test
        @DisplayName("성공: 연관 서비스 호출 + 멤버 삭제 + afterCommit 로그아웃 이벤트 발행")
        void success() {
            var me = persistMember("me@loop.in", "me", null);
            var dto = cu(me);

            memberService.deleteMember(dto, "accessToken");

            // 트랜잭션 안에서 delete 반영 확인 (state-based)
            assertThat(memberRepository.findById(me.getId())).isEmpty();

            // afterCommit 트리거를 위해 실제 commit
            TestTransaction.flagForCommit();
            TestTransaction.end();

            then(chatRoomService).should().leaveAllChatRooms(me.getId());
            then(teamService).should().deleteMyTeams(any(Member.class));
            then(loopService).should().deleteMyLoops(me.getId());
            then(authEventPublisher).should().publishLogoutAfterCommit(any(AuthPayload.class));
        }

        @Test
        @DisplayName("멤버 없으면 USER_NOT_FOUND")
        void notFound() {
            var fake = new CurrentUserDto(
                    999L, "x@x", null, "x", null, null, null,
                    null, Member.State.NORMAL, Member.MemberRole.ROLE_USER, Member.OAuthProvider.GOOGLE, "pid"
            );

            assertThatThrownBy(() -> memberService.deleteMember(fake, "t"))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.USER_NOT_FOUND);
        }
    }

    // =========================================================
    // searchMemberInfo: PageSize 제한(Static) + 실제 repo 쿼리 결과 smoke
    // =========================================================
    @Nested
    class SearchMemberInfo {

        @Test
        @DisplayName("페이지 사이즈 초과 -> PAGE_REQUEST_FAIL")
        void pageTooLarge() {
            try (MockedStatic<MemberPage> mocked = mockStatic(MemberPage.class)) {
                mocked.when(MemberPage::getMaxPageSize).thenReturn(50);
                Pageable pageable = PageRequest.of(0, 100);

                var me = persistMember("me@loop.in", "me", null);
                var dto = cu(me);

                assertThatThrownBy(() -> memberService.searchMemberInfo(pageable, "kw", dto))
                        .isInstanceOf(ServiceException.class)
                        .extracting("returnCode").isEqualTo(ReturnCode.PAGE_REQUEST_FAIL);
            }
        }

        @Test
        @DisplayName("검색 성공: PageResponse 반환 (스모크)")
        void successSmoke() {
            try (MockedStatic<MemberPage> mocked = mockStatic(MemberPage.class)) {
                mocked.when(MemberPage::getMaxPageSize).thenReturn(50);

                var me = persistMember("me@loop.in", "me", null);
                persistMember("a@loop.in", "alice", null);
                persistMember("b@loop.in", "bob", null);

                var dto = cu(me);
                Pageable pageable = PageRequest.of(0, 10, Sort.by("id").descending());

                PageResponse<MemberResponse> res = memberService.searchMemberInfo(pageable, "a", dto);
                assertThat(res).isNotNull();
                assertThat(res.getPageMeta()).isNotNull();
            }
        }
    }

    // =========================================================
    // followReq / cancelFollowReq / acceptFollowReq / refuseFollowReq
    // =========================================================
    @Nested
    class FollowFlow {

        @Test
        @DisplayName("팔로우 요청: 자기 자신 -> CANNOT_FOLLOW_SELF")
        void followReq_self() {
            var me = persistMember("me@loop.in", "me", null);
            var dto = cu(me);

            assertThatThrownBy(() -> memberService.followReq(me.getId(), dto))
                    .isInstanceOf(ServiceException.class)
                    .extracting("returnCode").isEqualTo(ReturnCode.CANNOT_FOLLOW_SELF);
        }

        @Test
        @DisplayName("팔로우 요청 성공: 요청 row 생성 + afterCommit 알림 발행")
        void followReq_success() {
            var me = persistMember("me@loop.in", "me", "img");
            var you = persistMember("you@loop.in", "you", null);
            var dto = cu(me);

            memberService.followReq(you.getId(), dto);

            assertThat(memberFollowReqRepository.findAll()).hasSize(1);

            TestTransaction.flagForCommit();
            TestTransaction.end();

            then(notificationEventPublisher).should()
                    .publishNotification(any(NotificationPayload.class), anyString());
        }

        @Test
        @DisplayName("팔로우 요청 취소: 요청 row 삭제")
        void cancelFollowReq_success() {
            var me = persistMember("me@loop.in", "me", null);
            var you = persistMember("you@loop.in", "you", null);
            var dto = cu(me);

            // 먼저 요청 생성
            MemberFollowReq req = memberFollowReqRepository.saveAndFlush(
                    MemberFollowReq.builder().followReq(me).followRec(you).build()
            );

            memberService.cancelFollowReq(you.getId(), dto);

            assertThat(memberFollowReqRepository.findById(req.getId())).isEmpty();
        }

        @Test
        @DisplayName("팔로우 요청 수락: 요청 삭제 + 팔로우 생성 + afterCommit 알림 발행")
        void acceptFollowReq_success() {
            var requester = persistMember("r@loop.in", "req", null);
            var receiver = persistMember("v@loop.in", "rec", "img");
            var receiverDto = cu(receiver);

            memberFollowReqRepository.saveAndFlush(
                    MemberFollowReq.builder().followReq(requester).followRec(receiver).build()
            );

            memberService.acceptFollowReq(requester.getId(), receiverDto);

            assertThat(memberFollowReqRepository.findAll()).isEmpty();
            assertThat(memberFollowRepository.findAll()).hasSize(1);
            MemberFollow follow = memberFollowRepository.findAll().get(0);
            assertThat(follow.getFollow().getId()).isEqualTo(requester.getId());
            assertThat(follow.getFollowed().getId()).isEqualTo(receiver.getId());

            TestTransaction.flagForCommit();
            TestTransaction.end();

            then(notificationEventPublisher).should()
                    .publishNotification(any(NotificationPayload.class), anyString());
        }

        @Test
        @DisplayName("팔로우 요청 거절: 요청 삭제")
        void refuseFollowReq_success() {
            var requester = persistMember("r@loop.in", "req", null);
            var receiver = persistMember("v@loop.in", "rec", null);
            var receiverDto = cu(receiver);

            memberFollowReqRepository.saveAndFlush(
                    MemberFollowReq.builder().followReq(requester).followRec(receiver).build()
            );

            memberService.refuseFollowReq(requester.getId(), receiverDto);

            assertThat(memberFollowReqRepository.findAll()).isEmpty();
        }

        @Test
        @DisplayName("팔로우 취소: 관계 삭제")
        void cancelFollow_success() {
            var me = persistMember("me@loop.in", "me", null);
            var you = persistMember("you@loop.in", "you", null);
            var dto = cu(me);

            MemberFollow relation = memberFollowRepository.saveAndFlush(
                    MemberFollow.builder().follow(me).followed(you).build()
            );

            memberService.cancelFollow(you.getId(), dto);

            assertThat(memberFollowRepository.findById(relation.getId())).isEmpty();
        }

        @Test
        @DisplayName("팔로워 목록에서 삭제(removeFollowed): 관계 삭제")
        void removeFollowed_success() {
            var follower = persistMember("f@loop.in", "f", null);
            var me = persistMember("me@loop.in", "me", null);
            var dto = cu(me);

            MemberFollow relation = memberFollowRepository.saveAndFlush(
                    MemberFollow.builder().follow(follower).followed(me).build()
            );

            memberService.removeFollowed(follower.getId(), dto);

            assertThat(memberFollowRepository.findById(relation.getId())).isEmpty();
        }
    }
}
