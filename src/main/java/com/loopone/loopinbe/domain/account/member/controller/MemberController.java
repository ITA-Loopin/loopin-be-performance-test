package com.loopone.loopinbe.domain.account.member.controller;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUser;
import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.dto.req.MemberUpdateRequest;
import com.loopone.loopinbe.domain.account.member.dto.res.DetailMemberResponse;
import com.loopone.loopinbe.domain.account.member.dto.res.MemberResponse;
import com.loopone.loopinbe.domain.account.member.entity.MemberPage;
import com.loopone.loopinbe.domain.account.member.service.MemberService;
import com.loopone.loopinbe.global.common.response.ApiResponse;
import com.loopone.loopinbe.global.web.cookie.WebAuthCookieFactory;
import com.loopone.loopinbe.global.security.TokenResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/rest-api/v1/member")
@RequiredArgsConstructor
@Tag(name = "Member", description = "회원 API")
public class MemberController {
    private final MemberService memberService;
    private final TokenResolver tokenResolver;
    private final WebAuthCookieFactory webAuthCookieFactory;

//    // 회원가입
//    @Operation(summary = "회원가입", description = "이메일과 닉네임으로 회원가입합니다.")
//    @PostMapping
//    public ApiResponse<Void> regularSignUp(@RequestBody @Valid MemberCreateRequest memberCreateRequest) {
//        memberService.regularSignUp(memberCreateRequest);
//        return ApiResponse.success();
//    }

    // 본인 회원정보 조회
    @GetMapping
    @Operation(summary = "본인 회원정보 조회", description = "현재 로그인된 사용자의 회원정보를 조회합니다.")
    public ApiResponse<MemberResponse> getMyInfo(@CurrentUser CurrentUserDto currentUser) {
        return ApiResponse.success(memberService.getMyInfo(currentUser));
    }

    // 본인 상세회원정보 조회
    @GetMapping("/detail")
    @Operation(summary = "본인 상세회원정보 조회", description = "현재 로그인된 사용자의 상세회원정보를 조회합니다.")
    public ApiResponse<DetailMemberResponse> getMyDetailInfo(@CurrentUser CurrentUserDto currentUser) {
        return ApiResponse.success(memberService.getMyDetailInfo(currentUser));
    }

    // 다른 사용자의 회원정보 조회
    @GetMapping("/{memberId}")
    @Operation(summary = "다른 사용자의 회원정보 조회", description = "다른 사용자의 회원정보를 조회합니다.")
    public ApiResponse<MemberResponse> getMemberInfo(@PathVariable("memberId") Long memberId) {
        return ApiResponse.success(memberService.getMemberInfo(memberId));
    }

    // 다른 사용자의 상세회원정보 조회
    @GetMapping("/detail/{memberId}")
    @Operation(summary = "다른 사용자의 상세회원정보 조회", description = "다른 사용자의 상세회원정보를 조회합니다.")
    public ApiResponse<DetailMemberResponse> getDetailMemberInfo(@PathVariable("memberId") Long memberId) {
        return ApiResponse.success(memberService.getDetailMemberInfo(memberId));
    }

    // 닉네임 중복 확인
    @GetMapping("/available")
    @Operation(summary = "닉네임 중복 확인", description = "입력한 닉네임의 중복 사용을 확인합니다.")
    public ApiResponse<String> checkNickname(@RequestParam(value = "nickname") String nickname){
        memberService.checkNickname(nickname);
        return ApiResponse.success("사용 가능한 닉네임입니다.");
    }

    // 회원정보 수정
    @Operation(summary = "회원정보 수정", description = "현재 로그인된 사용자의 회원정보를 수정합니다.")
    @PatchMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ApiResponse<Void> updateMemberInfo(@RequestPart(value = "memberUpdateRequest") @Valid MemberUpdateRequest memberUpdateRequest,
                                              @RequestPart(value = "imageFile", required = false) MultipartFile imageFile,
                                              @CurrentUser CurrentUserDto currentUser) {
        memberService.updateMember(memberUpdateRequest, imageFile, currentUser);
        return ApiResponse.success();
    }

    // 회원탈퇴
    @DeleteMapping
    @Operation(summary = "회원탈퇴", description = "현재 로그인된 사용자가 회원탈퇴합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteMember(HttpServletRequest request,
                                          @CurrentUser CurrentUserDto currentUser) {
        String accessToken = tokenResolver.resolveAccess(request);
        memberService.deleteMember(currentUser, accessToken);

        ResponseCookie accessCookie = webAuthCookieFactory.expireAccess();
        ResponseCookie refreshCookie = webAuthCookieFactory.expireRefresh();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success());
    }

    // 회원 검색하기
    @GetMapping("/search")
    @Operation(summary = "회원 검색", description = "현재 로그인된 사용자가 회원을 검색합니다.(기본설정: page=0, size=15)")
    public ApiResponse<List<MemberResponse>> searchMemberInfo(@ModelAttribute MemberPage request,
                                                              @RequestParam(value = "keyword") String keyword,
                                                              @CurrentUser CurrentUserDto currentUser) {
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        return ApiResponse.success(memberService.searchMemberInfo(pageable, keyword, currentUser));
    }

    // 팔로우 요청하기
    @PostMapping("/follow/{memberId}")
    @Operation(summary = "팔로우 요청", description = "현재 로그인된 사용자가 팔로우를 요청합니다.")
    public ApiResponse<Void> followReq(@PathVariable("memberId") Long memberId, @CurrentUser CurrentUserDto currentUser) {
        memberService.followReq(memberId, currentUser);
        return ApiResponse.success();
    }

    // 팔로우 요청 취소하기
    @DeleteMapping("/follow/{memberId}")
    @Operation(summary = "팔로우 요청 취소", description = "현재 로그인된 사용자가 팔로우 요청을 취소합니다.")
    public ApiResponse<Void> cancelFollowReq(@PathVariable("memberId") Long memberId, @CurrentUser CurrentUserDto currentUser) {
        memberService.cancelFollowReq(memberId, currentUser);
        return ApiResponse.success();
    }

    // 팔로우 요청 수락하기
    @PostMapping("/followReq/{memberId}")
    @Operation(summary = "팔로우 요청 수락", description = "현재 로그인된 사용자가 팔로우 요청을 수락합니다.")
    public ApiResponse<Void> acceptFollowReq(@PathVariable("memberId") Long memberId, @CurrentUser CurrentUserDto currentUser) {
        memberService.acceptFollowReq(memberId, currentUser);
        return ApiResponse.success();
    }

    // 팔로우 요청 거절하기
    @DeleteMapping("/followReq/{memberId}")
    @Operation(summary = "팔로우 요청 거절", description = "현재 로그인된 사용자가 팔로우 요청을 거절합니다.")
    public ApiResponse<Void> refuseFollowReq(@PathVariable("memberId") Long memberId, @CurrentUser CurrentUserDto currentUser) {
        memberService.refuseFollowReq(memberId, currentUser);
        return ApiResponse.success();
    }

    // 팔로우 취소하기
    @DeleteMapping("/followMember/{memberId}")
    @Operation(summary = "팔로우 취소", description = "현재 로그인된 사용자가 팔로우를 취소합니다.")
    public ApiResponse<Void> cancelFollow(@PathVariable("memberId") Long memberId, @CurrentUser CurrentUserDto currentUser) {
        memberService.cancelFollow(memberId, currentUser);
        return ApiResponse.success();
    }

    // 팔로워 목록에서 해당 유저 삭제하기
    @DeleteMapping("/followed/{memberId}")
    @Operation(summary = "팔로워 목록에서 삭제", description = "현재 로그인된 사용자가 팔로워 목록에서 삭제합니다.")
    public ApiResponse<Void> removeFollowed(@PathVariable("memberId") Long memberId, @CurrentUser CurrentUserDto currentUser) {
        memberService.removeFollowed(memberId, currentUser);
        return ApiResponse.success();
    }
}
