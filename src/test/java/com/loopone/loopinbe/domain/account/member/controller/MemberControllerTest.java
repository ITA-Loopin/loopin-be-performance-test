package com.loopone.loopinbe.domain.account.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserArgumentResolver;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.dto.req.MemberUpdateRequest;
import com.loopone.loopinbe.domain.account.member.dto.res.DetailMemberResponse;
import com.loopone.loopinbe.domain.account.member.dto.res.MemberResponse;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.enums.ProfileImageState;
import com.loopone.loopinbe.domain.account.member.service.MemberService;
import com.loopone.loopinbe.global.common.response.PageMeta;
import com.loopone.loopinbe.global.common.response.PageResponse;
import com.loopone.loopinbe.global.config.SecurityConfig;
import com.loopone.loopinbe.global.config.WebConfig;
import com.loopone.loopinbe.global.security.JwtAuthenticationFilter;
import com.loopone.loopinbe.global.security.TokenResolver;
import com.loopone.loopinbe.global.web.cookie.WebAuthCookieFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = MemberController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        JwtAuthenticationFilter.class,     // ← 보안 필터 컴포넌트 제외
                        SecurityConfig.class,              // ← 전역 보안 설정 클래스를 쓰고 있다면 같이 제외
                        WebConfig.class                    // ← 전역 WebMvcConfigurer가 보안/리졸버를 끌어오면 제외
                })
        },
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class MemberControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean MemberService memberService;
    @MockitoBean CurrentUserArgumentResolver currentUserArgumentResolver;
    @MockitoBean TokenResolver tokenResolver;
    @MockitoBean WebAuthCookieFactory webAuthCookieFactory;

    @BeforeEach
    void setUp() throws Exception {
        given(currentUserArgumentResolver.supportsParameter(any()))
                .willReturn(true);
        given(currentUserArgumentResolver.resolveArgument(any(), any(), any(), any()))
                .willReturn(new CurrentUserDto(
                        1L, "jun@loop.in", null, "jun", "010-0000-0000",
                        Member.Gender.MALE, LocalDate.of(2000,1,1),
                        null, Member.State.NORMAL, Member.MemberRole.ROLE_USER,
                        Member.OAuthProvider.GOOGLE, "provider-id"
                ));
    }

    // --- 성공 케이스: 본인 회원정보 조회 ---
    @Test
    @DisplayName("GET /rest-api/v1/member → 200 OK & ApiResponse 래퍼")
    void getMyInfo_success() throws Exception {
        var resp = new MemberResponse(1L, "jun@loop.in", "jun", "https://img");
        given(memberService.getMyInfo(any(CurrentUserDto.class))).willReturn(resp);

        mvc.perform(get("/rest-api/v1/member"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("jun@loop.in"))
                .andExpect(jsonPath("$.data.nickname").value("jun"));
    }

    // --- 성공 케이스: 본인 상세회원정보 조회 ---
    @Test
    @DisplayName("GET /rest-api/v1/member/detail → 200 OK")
    void getMyDetailInfo_success() throws Exception {
        var detail = DetailMemberResponse.builder()
                .id(1L).email("jun@loop.in").nickname("jun")
                .followMemberCount(10L).followedMemberCount(5L)
                .followList(List.of()).followedList(List.of()).followReqList(List.of()).followRecList(List.of())
                .build();
        given(memberService.getMyDetailInfo(any(CurrentUserDto.class))).willReturn(detail);

        mvc.perform(get("/rest-api/v1/member/detail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("jun@loop.in"))
                .andExpect(jsonPath("$.data.nickname").value("jun"))
                .andExpect(jsonPath("$.data.followMemberCount").value(10))
                .andExpect(jsonPath("$.data.followedMemberCount").value(5));
    }

    // --- 성공 케이스: 다른 사용자의 회원정보 조회 ---
    @Test
    @DisplayName("GET /rest-api/v1/member/{memberId} → 200 OK")
    void getMemberInfo_success() throws Exception {
        var resp = new MemberResponse(2L, "koo@loop.in", "koo", "https://img2");
        given(memberService.getMemberInfo(2L)).willReturn(resp);

        mvc.perform(get("/rest-api/v1/member/{memberId}", 2L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.email").value("koo@loop.in"))
                .andExpect(jsonPath("$.data.nickname").value("koo"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://img2"));
    }

    // --- 성공 케이스: 다른 사용자의 상세회원정보 조회 ---
    @Test
    @DisplayName("GET /rest-api/v1/member/detail/{memberId} → 200 OK")
    void getDetailMemberInfo_success() throws Exception {
        var detail = DetailMemberResponse.builder()
                .id(2L)
                .email("koo@loop.in")
                .nickname("koo")
                .profileImageUrl("https://img2")
                .followMemberCount(100L)     // 내가 팔로우하는 수
                .followedMemberCount(50L)    // 나를 팔로우하는 수
                .followList(List.of())       // 간단히 빈 리스트로
                .followedList(List.of())
                .followReqList(List.of())
                .followRecList(List.of())
                .build();
        given(memberService.getDetailMemberInfo(2L)).willReturn(detail);

        mvc.perform(get("/rest-api/v1/member/detail/{memberId}", 2))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.email").value("koo@loop.in"))
                .andExpect(jsonPath("$.data.nickname").value("koo"))
                .andExpect(jsonPath("$.data.profileImageUrl").value("https://img2"))
                .andExpect(jsonPath("$.data.followMemberCount").value(100))
                .andExpect(jsonPath("$.data.followedMemberCount").value(50));
    }

    // --- 검증/파라미터: 닉네임 중복 확인 ---
    @Test
    @DisplayName("GET /rest-api/v1/member/available?nickname=foo → 200 OK & 메시지")
    void checkNickname_available() throws Exception {
        mvc.perform(get("/rest-api/v1/member/available").param("nickname", "unique-nick"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("사용 가능한 닉네임입니다."));
        verify(memberService).checkNickname("unique-nick");
    }

    // --- 멀티파트 + ModelAttribute 검증: 회원정보 수정 ---
    @Test
    @DisplayName("PATCH /rest-api/v1/member (multipart + @RequestPart JSON) → 200 OK")
    void updateMemberInfo_success() throws Exception {
        // given: 컨트롤러는 @RequestPart("memberUpdateRequest")로 DTO를 받음 → JSON 파트로 넣어야 함
        var reqDto = new MemberUpdateRequest("newNick", ProfileImageState.UPDATE);
        var jsonPart = new MockMultipartFile(
                "memberUpdateRequest",                 // @RequestPart name과 동일해야 함
                "memberUpdateRequest.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(reqDto) // DTO를 JSON으로 직렬화
        );
        var filePart = new MockMultipartFile(
                "imageFile",                           // @RequestPart(value="imageFile")
                "p.png",
                MediaType.IMAGE_PNG_VALUE,
                "png".getBytes()
        );
        // when / then
        mvc.perform(multipart("/rest-api/v1/member")
                        .file(jsonPart)
                        .file(filePart)
                        .with(r -> { r.setMethod("PATCH"); return r; })
                        .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        // 서비스 호출 인자까지 컨트롤러 바인딩 결과로 검증 (권장)
        ArgumentCaptor<MemberUpdateRequest> dtoCaptor = ArgumentCaptor.forClass(MemberUpdateRequest.class);
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(memberService).updateMember(dtoCaptor.capture(), fileCaptor.capture(), any(CurrentUserDto.class));
        assertThat(dtoCaptor.getValue().nickname()).isEqualTo("newNick");
        assertThat(dtoCaptor.getValue().profileImageState()).isEqualTo(ProfileImageState.UPDATE);
        assertThat(fileCaptor.getValue()).isNotNull();
        assertThat(fileCaptor.getValue().getOriginalFilename()).isEqualTo("p.png");
    }

    @Test
    @DisplayName("PATCH /rest-api/v1/member (multipart + JSON part, imageFile 없음) → 200 OK")
    void updateMemberInfo_success_withoutImageFile() throws Exception {
        var reqDto = new MemberUpdateRequest("newNick", ProfileImageState.MAINTAIN);
        var jsonPart = new MockMultipartFile(
                "memberUpdateRequest",
                "memberUpdateRequest.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(reqDto)
        );
        mvc.perform(multipart("/rest-api/v1/member")
                        .file(jsonPart)
                        .with(r -> { r.setMethod("PATCH"); return r; })
                        .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        verify(memberService).updateMember(any(MemberUpdateRequest.class), isNull(), any(CurrentUserDto.class));
    }

    // --- 성공 케이스: 회원탈퇴 ---
    @Test
    @DisplayName("DELETE /rest-api/v1/member → 200 OK")
    void deleteMember_success() throws Exception {
        given(tokenResolver.resolveAccess(any())).willReturn("accessToken");
        given(webAuthCookieFactory.expireAccess())
                .willReturn(ResponseCookie.from("accessToken", "")
                        .path("/")
                        .maxAge(0)
                        .httpOnly(true)
                        .build());
        given(webAuthCookieFactory.expireRefresh())
                .willReturn(ResponseCookie.from("refreshToken", "")
                        .path("/")
                        .maxAge(0)
                        .httpOnly(true)
                        .build());
        willDoNothing().given(memberService).deleteMember(any(CurrentUserDto.class), eq("accessToken"));

        mvc.perform(delete("/rest-api/v1/member"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    var setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    assertThat(setCookies).hasSize(2);
                    assertThat(setCookies.get(0)).contains("accessToken=");
                    assertThat(setCookies.get(1)).contains("refreshToken=");
                });
        verify(tokenResolver).resolveAccess(any());
        verify(memberService).deleteMember(any(CurrentUserDto.class), eq("accessToken"));
        verify(webAuthCookieFactory).expireAccess();
        verify(webAuthCookieFactory).expireRefresh();
    }

    // --- 성공 케이스: 회원 검색 (page/size 기본 파라미터 포함) ---
    @Test
    @DisplayName("GET /rest-api/v1/member/search?keyword=a → 200 OK (기본 page=0,size=15)")
    void searchMemberInfo_success() throws Exception {
        var m1 = new MemberResponse(3L, "user3@example.com", "gangneung", null);
        var m2 = new MemberResponse(4L, "user4@example.com", "busan",     null);

        Page<MemberResponse> page = new PageImpl<>(
                List.of(m1, m2),
                PageRequest.of(0, 15),
                2 // totalElements
        );
        PageMeta meta = PageMeta.of(page);

        // 실제 PageResponse 객체를 만들어 반환해야 함
        PageResponse<MemberResponse> pageResponse = PageResponse.of(page);
        given(memberService.searchMemberInfo(any(Pageable.class), anyString(), any(CurrentUserDto.class)))
                .willReturn(pageResponse);

        mvc.perform(get("/rest-api/v1/member/search").param("keyword", "a"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))

                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data.[*].nickname",
                        containsInAnyOrder("gangneung", "busan")))

                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(15))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.first").value(true))
                .andExpect(jsonPath("$.page.last").value(true))
                .andExpect(jsonPath("$.page.hasNext").value(false));

        // 기본 page/size가 0/15로 내려오는지 검증
        verify(memberService).searchMemberInfo(
                argThat(p -> p.getPageNumber() == 0 && p.getPageSize() == 15),
                eq("a"),
                any(CurrentUserDto.class)
        );
    }

    // --- 성공 케이스: 팔로우 요청하기 ---
    @Test
    @DisplayName("POST /rest-api/v1/member/follow/{memberId} → 200 OK")
    void followReq_success() throws Exception {
        mvc.perform(post("/rest-api/v1/member/follow/{memberId}", 7L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        verify(memberService).followReq(eq(7L), any(CurrentUserDto.class));
    }

    // --- 성공 케이스: 팔로우 요청 취소하기 ---
    @Test
    @DisplayName("DELETE /rest-api/v1/member/follow/{memberId} → 200 OK")
    void cancelFollowReq_success() throws Exception {
        mvc.perform(delete("/rest-api/v1/member/follow/{memberId}", 7L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        verify(memberService).cancelFollowReq(eq(7L), any(CurrentUserDto.class));
    }

    // --- 성공 케이스: 팔로우 요청 수락하기 ---
    @Test
    @DisplayName("POST /rest-api/v1/member/followReq/{memberId} → 200 OK")
    void acceptFollowReq_success() throws Exception {
        mvc.perform(post("/rest-api/v1/member/followReq/{memberId}", 7L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        verify(memberService).acceptFollowReq(eq(7L), any(CurrentUserDto.class));
    }

    // --- 성공 케이스: 팔로우 요청 거절하기 ---
    @Test
    @DisplayName("DELETE /rest-api/v1/member/followReq/{memberId} → 200 OK")
    void refuseFollowReq_success() throws Exception {
        mvc.perform(delete("/rest-api/v1/member/followReq/{memberId}", 7L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        verify(memberService).refuseFollowReq(eq(7L), any(CurrentUserDto.class));
    }

    // --- 성공 케이스: 팔로우 취소하기 ---
    @Test
    @DisplayName("DELETE /rest-api/v1/member/followMember/{memberId} → 200 OK")
    void cancelFollow_success() throws Exception {
        mvc.perform(delete("/rest-api/v1/member/followMember/{memberId}", 7L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        verify(memberService).cancelFollow(eq(7L), any(CurrentUserDto.class));
    }

    // --- 성공 케이스: 팔로워 목록에서 해당 유저 삭제하기 ---
    @Test
    @DisplayName("DELETE /rest-api/v1/member/followed/{memberId} → 200 OK")
    void removeFollowed_success() throws Exception {
        mvc.perform(delete("/rest-api/v1/member/followed/{memberId}", 7L))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        verify(memberService).removeFollowed(eq(7L), any(CurrentUserDto.class));
    }
}
