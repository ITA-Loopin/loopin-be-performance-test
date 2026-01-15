package com.loopone.loopinbe.domain.account.member.entity;

import com.loopone.loopinbe.domain.account.member.dto.req.MemberUpdateRequest;
import com.loopone.loopinbe.global.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@DynamicInsert
public class Member extends BaseEntity {
    @Column(length = 50, nullable = false)
    private String email;

    @Column(length = 1000)
    private String password;

    @Column(length = 10)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    private Gender gender;  // 성별
    public enum Gender {
        MALE, FEMALE;
    }

    private LocalDate birthday;

    @Column(length = 300)
    private String profileImageUrl = "";  // 프로필 사진 경로

    @Enumerated(EnumType.STRING)
    @Column(length = 6, nullable = false)
    @Builder.Default
    private State state = State.NORMAL;  // 회원 상태
    public enum State {
        NORMAL, BANNED;
    }

    @Enumerated(EnumType.STRING)
    @Column(length = 9, nullable = false)
    @Builder.Default
    private MemberRole role = MemberRole.ROLE_USER;  // 권한 (관리자, 사용자)
    public enum MemberRole {
        ROLE_USER, ROLE_ADMIN;
    }

    @Enumerated(EnumType.STRING)
    @Column(length = 6)
    @Builder.Default
    private OAuthProvider oAuthProvider = OAuthProvider.NONE;
    public enum OAuthProvider {
        NAVER, KAKAO, GOOGLE, NONE;
        // provider raw 문자열을 enum으로 변환
        public static OAuthProvider from(String raw) {
            if (raw == null) return NONE;
            switch (raw.trim().toLowerCase()) {
                case "google": return GOOGLE;
                case "kakao":  return KAKAO;
                case "naver":  return NAVER;
                default:       return NONE;
            }
        }
        // application.yml 내 providers 맵 key 접근용 (google/kakao/naver)
        public String key() { return name().toLowerCase(); }
    }

    @Column(length = 100)
    private String providerId;

    @OneToMany(mappedBy = "follow", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberFollow> followList = new ArrayList<>();

    @OneToMany(mappedBy = "followed", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberFollow> followedList = new ArrayList<>();

    @OneToMany(mappedBy = "followReq", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberFollowReq> followReqList = new ArrayList<>();

    @OneToMany(mappedBy = "followRec", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MemberFollowReq> followRecList = new ArrayList<>();

    // ----------------- 회원정보 수정 메서드 -----------------

    public void update(MemberUpdateRequest req, String newProfileImageUrl, PasswordEncoder passwordEncoder) {
//        if (req.email() != null) this.email = req.email();
//        if (req.password() != null) this.password = passwordEncoder.encode(req.password());
        if (req.nickname() != null) this.nickname = req.nickname();
//        if (req.phone() != null) this.phone = req.phone();
//        if (req.gender() != null) this.gender = req.gender();
//        if (req.birthday() != null) this.birthday = req.birthday();
        this.profileImageUrl = newProfileImageUrl; // 항상 업데이트
    }
}
