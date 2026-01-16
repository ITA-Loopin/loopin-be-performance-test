package com.loopone.loopinbe.global.exception;

import lombok.Getter;

@Getter
public enum ReturnCode {
    // 공통
    SUCCESS(200, "SUCCESS_001", "요청에 성공하였습니다."),
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    BAD_REQUEST(400, "COMMON_400", "적절하지 않은 요청입니다."),
    UNAUTHORIZED(401, "COMMON_401", "인증이 필요합니다."),
    FORBIDDEN(403, "COMMON_403", "권한이 없습니다."),
    NOT_FOUND(404, "COMMON_404", "대상을 찾을 수 없습니다."),
    CONFLICT(409, "COMMON_409", "요청이 현재 리소스 상태와 충돌합니다."),

    // Page
    PAGE_REQUEST_FAIL(400, "PAGE_001", "적절하지 않은 페이지 요청입니다."),

    // Auth
    INVALID_AUTH_TOKEN(401, "AUTH_001", "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(401, "AUTH_002", "만료된 토큰입니다."),
    SOCIAL_ONLY_ACCOUNT(400, "AUTH_003", "소셜 로그인으로만 가입된 계정입니다. 비밀번호를 먼저 설정해주세요."),
    INVALID_PASSWORD(401, "AUTH_004", "비밀번호가 일치하지 않습니다."),
    INVALID_EMAIL(404, "AUTH_005", "해당 이메일로 가입된 정보가 없습니다."),
    AUTH_UNAUTHORIZED(401, "AUTH_006", "인증된 사용자 정보가 없습니다."),
    REDIRECT_URL_NOT_FOUND(400, "AUTH_007", "리다이렉트 URL을 찾을 수 없습니다."),
    PASSWORD_REQUIRED(400, "AUTH_008", "비밀번호는 필수 입력값입니다."),
    EMAIL_REQUIRED(400, "AUTH_009", "이메일은 필수 입력값입니다."),
    CURRENT_PASSWORD_NOT_MATCH(400, "AUTH_010", "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_CHANGE_FAILED(500, "AUTH_011", "비밀번호 변경에 실패했습니다."),
    EMAIL_NOT_VERIFIED(400, "AUTH_012", "이메일 인증을 완료 후 회원가입 진행해주세요."),
    SOCIAL_ACCOUNT_ALREADY_IN_USE(409, "AUTH_013", "이미 사용중인 소셜 계정입니다."),
    INVALID_ACCOUNT_ID(400, "AUTH_014", "유효하지 않은 계정ID입니다."),
    NOT_AUTHORIZED(401, "AUTH_015", "권한이 없습니다."),
    EMAIL_ALREADY_USED(409, "AUTH_016", "이미 사용중인 이메일입니다."),

    // Member
    MEMBER_ALREADY_EXISTS(409, "MEMBER_001", "이미 존재하는 사용자입니다."),
    NICKNAME_ALREADY_USED(409, "MEMBER_002", "이미 사용중인 닉네임입니다."),
    PROFILE_IMAGE_REQUIRED(400, "MEMBER_003", "프로필 이미지가 필요합니다."),

    // User
    USER_NOT_FOUND(404, "USER_001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(409, "USER_002", "이미 사용중인 이메일입니다."),
    INVALID_LOGIN_TYPE(400, "USER_003", "지원하지 않는 로그인 타입입니다."),
    INVALID_ROLE(403, "USER_004", "권한이 없습니다."),
    PASSWORD_RESET_NOT_VERIFIED(400, "USER_005", "비밀번호 재설정 인증이 필요합니다."),
    INVALID_PHONE_NUMBER(404, "USER_006", "해당 전화번호로 등록된 계정을 찾을 수 없습니다."),
    ALREADY_CONNECTED_SOCIAL_ACCOUNT(409, "USER_007", "이미 연결된 소셜 계정입니다."),
    NOT_CONNECTED_SOCIAL_ACCOUNT(400, "USER_008", "연결되지 않은 소셜 계정입니다."),
    ALREADY_HAS_PASSWORD(409, "USER_009", "이미 비밀번호가 설정되어 있습니다."),
    CANNOT_DISCONNECT_LAST_LOGIN_METHOD(400, "USER_010", "마지막 로그인 수단은 연결을 해제할 수 없습니다."),
    DISCONNECT_FAIL(500, "USER_011", "소셜 계정 연동 해제에 실패했습니다."),
    INVALID_SOCIAL_CONNECTION(400, "USER_012", "기존 정보를 찾지 못하여 소셜 연동이 실패했습니다."),
    PASSWORD_VERIFICATION_REQUIRED(400, "USER_013", "비밀번호 확인이 필요합니다."),
    INVALID_VERIFICATION_CODE(400, "USER_014", "인증코드가 올바르지 않습니다."),
    INVALID_RESET_TOKEN(400, "USER_014", "비밀번호 재설정 토큰이 올바르지 않습니다."),

    // Report
    REPORT_NOT_FOUND(404, "REPORT_001", "해당 신고를 찾을 수 없습니다."),
    REPORTER_NOT_FOUND(404, "REPORT_002", "신고자를 찾을 수 없습니다."),
    REPORTED_NOT_FOUND(404, "REPORT_003", "신고 대상자를 찾을 수 없습니다."),
    REPORTER_SELF_REPORT(400, "REPORT_004", "자기 자신을 신고할 수 없습니다."),

    // Storage
    FILE_NOT_FOUND(404, "STORAGE_001", "파일을 찾을 수 없습니다."),
    FILE_UPLOAD_ERROR(500, "STORAGE_002", "파일 업로드에 실패했습니다."),
    FILE_DELETE_ERROR(500, "STORAGE_003", "파일 삭제에 실패했습니다."),
    FILE_CONTENT_TYPE_ERROR(500, "STORAGE_004", "파일의 content-type이 적절하지 않습니다."),
    MAX_FILE_LIMIT_EXCEEDED(400, "STORAGE_005", "파일 최대 전송 수량을 초과했습니다."),
    MAX_FILE_SIZE_LIMIT_EXCEEDED(400, "STORAGE_006", "파일 최대 전송 용량을 초과했습니다."),

    // Email
    EMAIL_SEND_FAIL(500, "EMAIL_001", "이메일 전송에 실패했습니다."),
    EMAIL_VERIFICATION_NOT_FOUND(404, "EMAIL_002", "인증 코드가 존재하지 않습니다."),
    EMAIL_VERIFICATION_EXPIRED(400, "EMAIL_003", "만료된 인증 코드입니다."),
    EMAIL_VERIFICATION_NOT_MATCH(400, "EMAIL_004", "인증 코드가 일치하지 않습니다."),

    // SMS
    SMS_SEND_FAILED(500, "SMS_001", "SMS 발송에 실패했습니다."),
    SMS_CODE_EXPIRED(400, "SMS_002", "만료된 인증번호입니다."),
    SMS_RESEND_TOO_EARLY(400, "SMS_003", "재전송 대기 시간이 아직 남았습니다."),
    SMS_DAILY_LIMIT_EXCEEDED(400, "SMS_004", "하루 최대 전송 횟수를 초과했습니다."),
    INVALID_SNS_VERIFICATION_CODE(400, "SMS_005", "인증번호가 일치하지 않습니다."),
    PHONE_NUMBER_NOT_VERIFIED(400, "SMS_006", "휴대폰 번호는 인증을 완료 후 추가할 수 있습니다."),
    ALREADY_VERIFIED_PHONE_NUMBER(400, "SMS_007", "이미 인증된 휴대폰 번호입니다."),

    // ChatRoom
    CHATROOM_NOT_FOUND(404, "CHATROOM_001", "채팅방을 찾을 수 없습니다."),
    CHATROOM_ALREADY_EXISTS(409, "CHATROOM_002", "이미 존재하는 채팅방입니다."),
    CHATROOM_LIMIT_EXCEEDED(400, "CHATROOM_003", "채팅방 참여자 수가 초과했습니다."),
    CHATROOM_MEMBER_ALREADY_EXISTS(409, "CHATROOM_004", "이미 채팅방에 존재하는 사용자입니다."),
    INVALID_KICK_MEMBER(400, "CHATROOM_005", "강퇴할 수 없는 사용자입니다."),
    INVALID_DELEGATE_MEMBER(400, "CHATROOM_006", "위임할 수 없는 사용자입니다."),

    // ChatRoomMember
    CHATROOM_MEMBER_NOT_FOUND(404, "CHATROOM_MEMBER_001", "채팅방 멤버를 찾을 수 없습니다."),

    // ChatMessage clientMessageId
    CHATMESSAGE_NOT_FOUND(404, "CHATMESSAGE_001", "채팅 메시지를 찾을 수 없습니다."),
    CHATMESSAGE_INVALID_TYPE(400, "CHATMESSAGE_002", "메세지 타입은 CREATE_LOOP 또는 UPDATE_LOOP이여야 합니다"),

    // Post
    POST_NOT_FOUND(404, "POST_001", "게시글을 찾을 수 없습니다."),
    POST_ALREADY_SAVED(409, "POST_002", "이미 저장한 게시글입니다."),
    POST_ALREADY_LIKED(409, "POST_003", "이미 좋아요를 누른 게시글입니다."),

    // Comment
    COMMENT_NOT_FOUND(404, "COMMENT_001", "댓글을 찾을 수 없습니다."),
    COMMENT_ALREADY_LIKED(409, "COMMENT_002", "이미 좋아요를 누른 댓글입니다."),

    // Loop
    LOOP_NOT_FOUND(404, "LOOP_001", "루프를 찾을 수 없습니다."),
    LOOP_RULE_NOT_FOUND(404, "LOOP_002", "루프 그룹을 찾을 수 없습니다."),
    CHECK_LIST_NOT_FOUND(404, "LOOP_003", "체크리스트를 찾을 수 없습니다."),
    LOOP_ACCESS_DENIED(403, "LOOP_004", "해당 루프에 대한 권한이 없습니다."),
    CHECKLIST_ACCESS_DENIED(403, "LOOP_005", "해당 체크리스트에 대한 권한이 없습니다."),
    OPEN_AI_INTERNAL_ERROR(500, "LOOP_006", "OpenAI 루프 생성 중 오류가 발생했습니다."),
    OPEN_AI_UNAUTHORIZED(401, "LOOP_007", "OpenAI 인증에 실패했습니다."),
    OPEN_AI_RATE_LIMIT(429, "LOOP_008", "OpenAI 요청 수를 초과하였습니다."),
    OPEN_AI_JSON_PROCESSING_ERROR(500, "LOOP_009", "OpenAI JSON 직렬화에 실패했습니다."),
    UNKNOWN_SCHEDULE_TYPE(404, "LOOP_010", "올바르지 않은 스케쥴 타입입니다."),

    // Team
    TEAM_NOT_FOUND(404, "TEAM_001", "팀을 찾을 수 없습니다."),
    INVALID_REQUEST_TEAM(400, "TEAM_002", "팀 요청문이 올바르지 않습니다"),
    USER_NOT_IN_TEAM(403, "TEAM_003", "팀원이 아닌 유저가 있습니다"),
    TEAM_LOOP_NOT_FOUND(404, "TEAM_004", "팀 루프를 찾을 수 없습니다."),
    NOT_PARTICIPATING_IN_LOOP(404, "TEAM_005", "해당 루프에 참여하지 않았습니다."),
    PROGRESS_NOT_FOUND(404, "TEAM_006", "팀 루프의 내 진행 상태를 찾을 수 없습니다."),
    MEMBER_NOT_FOUND(404, "TEAM_007", "멤버를 찾을 수 없습니다."),
    ALREADY_TEAM_MEMBER(409, "TEAM_008", "이미 팀원입니다."),
    INVITATION_ALREADY_SENT(409, "TEAM_009", "이미 초대를 보냈습니다."),
    INVITATION_NOT_FOUND(404, "TEAM_010", "초대를 찾을 수 없습니다."),
    INVALID_INVITATION(400, "TEAM_011", "유효하지 않은 초대입니다."),
    INVITATION_ALREADY_RESPONDED(400, "TEAM_012", "이미 응답한 초대입니다."),
    UNAUTHORIZED_TEAM_LEADER_ONLY(403, "TEAM_013", "팀 리더만 수행할 수 있습니다."),
    UNAUTHORIZED_INVITATION_RECIPIENT_ONLY(403, "TEAM_014", "초대받은 사람만 수행할 수 있습니다."),
    TEAM_LEADER_CANNOT_LEAVE(403, "TEAM_015", "팀 리더는 팀을 나갈 수 없습니다."),
    CANNOT_REMOVE_SELF(400, "TEAM_016", "자기 자신은 삭제할 수 없습니다."),

    // Follow
    ALREADY_REQUESTED(409, "FOLLOW_001", "이미 팔로우 요청을 보냈습니다."),
    REQUEST_NOT_FOUND(404, "FOLLOW_002", "팔로우 요청을 찾을 수 없습니다."),
    ALREADY_FOLLOW(409, "FOLLOW_003", "이미 팔로우 중인 사용자입니다."),
    FOLLOWER_NOT_FOUND(404, "FOLLOW_004", "팔로워를 찾을 수 없습니다."),
    FOLLOW_NOT_FOUND(404, "FOLLOW_005", "팔로우한 사용자를 찾을 수 없습니다."),
    CANNOT_FOLLOW_SELF(404, "FOLLOW_006", "자기 자신을 팔로우 할 수 없습니다."),

    // Notification
    NOTIFICATION_NOT_FOUND(404, "NOTIFICATION_001", "해당 알림을 찾을 수 없습니다."),
    MAP_TO_JSON_FAILED(500, "NOTIFICATION_002", "맵을 JSON으로 변환하는데 실패했습니다."),
    NAVI_VALIDATE_ERROR(400, "NOTIFICATION_003", "navigationData에 필수 키가 없습니다."),
    ARGS_VALIDATE_ERROR(400, "NOTIFICATION_004", "메시지 인자가 부족합니다."),

    // System/Business
    INTERNAL_ERROR(500, "SYS_001", "내부 시스템 에러"),
    API_CALL_FAILED(500, "SYS_002", "외부 API 호출에 실패했습니다."),

    // ELK Search
    DATA_CONVERSION_ERROR(500, "SEARCH_001", "데이터 변환 중 오류가 발생했습니다."),
    SEARCH_POST_ERROR(500, "SEARCH_002", "엘라스틱 서치 검색 중 에러 발생"),

    // Kafka
    KAFKA_SEND_ERROR(500, "KAFKA_001", "카프카 전송 중 오류가 발생했습니다."),
    ;

    private final int status;
    private final String code;
    private final String message;

    ReturnCode(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}
