package com.loopone.loopinbe.global.initData.loop.service;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.converter.MemberConverter;
import com.loopone.loopinbe.domain.account.member.entity.Member;
import com.loopone.loopinbe.domain.account.member.repository.MemberRepository;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import com.loopone.loopinbe.domain.loop.loop.entity.Loop;
import com.loopone.loopinbe.domain.loop.loop.enums.RepeatType;
import com.loopone.loopinbe.domain.loop.loop.repository.LoopRepository;
import com.loopone.loopinbe.domain.loop.loop.service.LoopService;
import com.loopone.loopinbe.domain.loop.loopChecklist.entity.LoopChecklist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotProdLoopService {
    private final LoopService loopService;
    private final LoopRepository loopRepository;
    private final MemberRepository memberRepository;
    private final MemberConverter memberConverter;
    private static final String USER1_EMAIL = "user1@example.com";
    private static final String USER_EMAIL_FORMAT = "user%d@example.com";
    private static final String LOOP_TRAVEL = "강릉 당일치기";
    private static final String LOOP_RUNNING = "동계 런닝 훈련";
    private static final String LOOP_TOEIC = "토익 공부하기";
    private static final String LOOP_CODING = "코딩 테스트 준비";

    // [유저 1이 주간 루프 생성]
    // 0) 단일 루프(오늘) - 강릉 당일치기
    // 1) 반복 루프(주 2회: 월/수, 이번주/저번주) - 동계 런닝 훈련
    // 2) 반복 루프(주 2회: 월/수, 이번주/저번주) - 토익 공부하기
    @Transactional
    public void createWeekLoops() {
        CurrentUserDto user1 = user1CurrentUser();
        LocalDate today = LocalDate.now();
        LocalDate thisWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastWeekMonday = thisWeekMonday.minusWeeks(1);
        LocalDate thisWeekSunday = thisWeekMonday.plusDays(6);
        // 0) 단일 루프: 강릉 당일치기
        loopService.createLoop(
                new LoopCreateRequest(
                        LOOP_TRAVEL,
                        "친구들이랑 여행",
                        RepeatType.NONE,
                        today,
                        null,
                        null,
                        null,
                        List.of("주문진 해수욕장"),
                        null
                ),
                user1
        );
        // 1) 반복 루프: 동계 런닝 훈련 (월/수) - 이번주/저번주
        loopService.createLoop(
                new LoopCreateRequest(
                        LOOP_RUNNING,
                        "3km 10분 달성 목표",
                        RepeatType.WEEKLY,
                        null,
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        lastWeekMonday,
                        thisWeekSunday,
                        List.of("아침에 3km 런닝", "런닝 후 샐러드 건강식 먹기"),
                        null
                ),
                user1
        );
        // 2) 반복 루프: 토익 공부하기 (월/수) - 이번주/저번주
        loopService.createLoop(
                new LoopCreateRequest(
                        LOOP_TOEIC,
                        "950점 목표",
                        RepeatType.WEEKLY,
                        null,
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        lastWeekMonday,
                        thisWeekSunday,
                        List.of("아침에 오답단어 복습", "듣기 연습", "기출 1회 풀기", "오답노트하기"),
                        null
                ),
                user1
        );
        log.info("[NotProd] createWeekLoops done for user1={}", USER1_EMAIL);
    }

    // [유저 1이 주간 체크리스트 완료 처리(1)-1]
    // 0) 단일 루프 완료(2025-12-25)
    // 1) 동계 런닝 훈련(12/22~12/24): MONDAY(1번만 완료), WEDNESDAY(전체 완료)
    // 2) 토익 공부하기(12/22~12/24): MONDAY/WEDNESDAY(1,2,3번만 완료)
    @Transactional
    public void completeScenario_1_1() {
        Long user1Id = user1Id();

        // 0) 강릉 당일치기 단일 루프 체크리스트 완료
        completeAllChecklists(user1Id, LOOP_TRAVEL, LocalDate.of(2025, 12, 25));

        // 1) 동계 런닝 훈련 (12/22~12/24)
        LocalDate start = LocalDate.of(2025, 12, 22);
        LocalDate end = LocalDate.of(2025, 12, 24);

        // MONDAY: 1번만 완료
        for (LocalDate d : datesOf(DayOfWeek.MONDAY, start, end)) {
            completeChecklistIndices(user1Id, LOOP_RUNNING, d, Set.of(1));
        }
        // WEDNESDAY: 전체 완료
        for (LocalDate d : datesOf(DayOfWeek.WEDNESDAY, start, end)) {
            completeAllChecklists(user1Id, LOOP_RUNNING, d);
        }
        // 2) 토익 공부하기 (12/22~12/24) - 월/수 모두 1,2,3번만 완료 (4번 미완료)
        Set<Integer> toeicPartial = Set.of(1, 2, 3);
        for (LocalDate d : datesOf(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), start, end)) {
            completeChecklistIndices(user1Id, LOOP_TOEIC, d, toeicPartial);
        }
        log.info("[NotProd] completeScenario_1_1 done");
    }

    // [유저 1이 주간 체크리스트 완료 처리(1)-2]
    // 0) 단일 루프 완료(2025-12-25)
    // 1) 동계 런닝 훈련(12/22~12/24): MONDAY(1번만 완료), WEDNESDAY(모두 미완료)
    // 2) 토익 공부하기(12/22~12/24): MONDAY/WEDNESDAY(1번만 완료)
    // 3) 코딩 테스트 준비(12/24~12/31, 목/금): 해당 생성된 모든 날짜 전체 완료
    @Transactional
    public void completeScenario_1_2() {
        Long user1Id = user1Id();

        // 0) 강릉 당일치기 단일 루프 체크리스트 완료
        completeAllChecklists(user1Id, LOOP_TRAVEL, LocalDate.of(2025, 12, 25));

        // 1) 동계 런닝 훈련 (12/22~12/24)
        LocalDate start = LocalDate.of(2025, 12, 22);
        LocalDate end = LocalDate.of(2025, 12, 24);

        // MONDAY: 1번만 완료
        for (LocalDate d : datesOf(DayOfWeek.MONDAY, start, end)) {
            completeChecklistIndices(user1Id, LOOP_RUNNING, d, Set.of(1));
        }
        // WEDNESDAY: 모두 미완료 (빈 Set)
        for (LocalDate d : datesOf(DayOfWeek.WEDNESDAY, start, end)) {
            completeChecklistIndices(user1Id, LOOP_RUNNING, d, Set.of());
        }
        // 2) 토익 공부하기 (12/22~12/24) - 월/수 모두 1번만 완료
        for (LocalDate d : datesOf(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), start, end)) {
            completeChecklistIndices(user1Id, LOOP_TOEIC, d, Set.of(1));
        }
        log.info("[NotProd] completeScenario_1_2 done");
    }

    // [유저 N이 월간 루프 생성]
    // 0) 단일 루프(당일)
    // 1) 반복 루프(주 2회: 월/수, 이번달 내내) - 동계 런닝 훈련
    // 2) 반복 루프(주 2회: 월/수, 이번달 내내) - 토익 공부하기
    // 3) 반복 루프(주 1회: 화, 이번달 내내) - 코딩 테스트 준비
    public void createMonthLoops(int fromUserNo, int toUserNo) {
        if (fromUserNo <= 0 || toUserNo <= 0) {
            throw new IllegalArgumentException("userNo must be positive. from=" + fromUserNo + ", to=" + toUserNo);
        }
        if (fromUserNo > toUserNo) {
            throw new IllegalArgumentException("fromUserNo must be <= toUserNo. from=" + fromUserNo + ", to=" + toUserNo);
        }
        int success = 0;
        int fail = 0;

        for (int userNo = fromUserNo; userNo <= toUserNo; userNo++) {
            try {
                createMonthLoopsForUser(userNo); // 유저 단위 트랜잭션
                success++;
            } catch (Exception e) {
                fail++;
                log.warn("[NotProd] createMonthLoops failed for userNo={} (email={}). reason={}",
                        userNo, userEmail(userNo), e.getMessage(), e);
            }
        }
        log.info("[NotProd] createMonthLoops range done. from={}, to={}, success={}, fail={}",
                fromUserNo, toUserNo, success, fail);
    }

    // 유저 한 명 단위로 트랜잭션 분리 (배치 안정성)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createMonthLoopsForUser(int userNo) {
        CurrentUserDto user = userCurrentUser(userNo);
        LocalDate today = LocalDate.now();
        LocalDate thisMonthStart = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate thisMonthEnd = today.with(TemporalAdjusters.lastDayOfMonth());
        // 이번주 월요일
        LocalDate thisWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 저번주 월요일
        LocalDate lastWeekMonday = thisWeekMonday.minusWeeks(1);
        // 이번주 일요일
        LocalDate thisWeekSunday = thisWeekMonday.plusDays(6);
        // 0) 단일 루프: 당일 치기 여행
        loopService.createLoop(
                new LoopCreateRequest(
                        LOOP_TRAVEL,
                        "친구들이랑 여행",
                        RepeatType.NONE,
                        today,
                        null,
                        null,
                        null,
                        List.of("주문진 해수욕장"),
                        null
                ),
                user
        );
        // 1) 반복 루프: 동계 런닝 훈련 (월/수) - 이번달 내내
        loopService.createLoop(
                new LoopCreateRequest(
                        LOOP_RUNNING,
                        "3km 10분 달성 목표",
                        RepeatType.WEEKLY,
                        null,
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        thisMonthStart,
                        thisMonthEnd,
                        List.of("런닝 전 스트레칭", "아침에 3km 런닝", "런닝 후 1km 조깅", "마무리 스트레칭", "런닝 후 샐러드 건강식 먹기"),
                        null
                ),
                user
        );
        // 2) 반복 루프: 토익 공부하기 (월/수) - 이번달 내내
        loopService.createLoop(
                new LoopCreateRequest(
                        LOOP_TOEIC,
                        "950점 목표",
                        RepeatType.WEEKLY,
                        null,
                        List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                        thisMonthStart,
                        thisMonthEnd,
                        List.of("아침에 오답단어 복습", "듣기 연습", "기출 1회 풀기", "오답노트하기", "오답 단어 1회독"),
                        null
                ),
                user
        );
        // 3) 반복 루프: 코딩 테스트 준비 (화)
        loopService.createLoop(
                new LoopCreateRequest(
                        LOOP_CODING,
                        "1일 1회 기출 풀기",
                        RepeatType.WEEKLY,
                        null,
                        List.of(DayOfWeek.TUESDAY),
                        lastWeekMonday,
                        thisWeekSunday,
                        List.of("카카오 기출 1회 풀기"),
                        null
                ),
                user
        );
        log.info("[NotProd] createMonthLoops done for userNo={} email={}", userNo, userEmail(userNo));
    }

    // [유저 1이 체크리스트 완료 처리(2)-1(잘한 루프 선정 검증)]
    // 0) 단일 루프 완료(2025-12-25 체크리스트 완료)
    // 1) 동계 런닝 훈련: 5주(12/1~12/31)
    //    - MONDAY: (1-4번 체크리스트 완료)
    //    - WEDNESDAY: (1-4번 체크리스트 완료)
    // 2) 토익 공부하기: 4주(12/1~12/31)
    //    - MONDAY/WEDNESDAY: (모든 체크리스트 완료)
    //    - 마지막 all complete 대상 루프는 12/24(WEDNESDAY)
    @Transactional
    public void completeScenario_2_1() {
        Long user1Id = user1Id();
        // 0) 강릉 당일치기 단일 루프 체크리스트 완료
        completeAllChecklists(user1Id, LOOP_TRAVEL, LocalDate.of(2025, 12, 25));

        // 1) 동계 런닝 훈련 (12/1~12/31) - 매주 월/수 1~4번 체크리스트 완료
        LocalDate start = LocalDate.of(2025, 12, 1);
        LocalDate end = LocalDate.of(2025, 12, 31);
        Set<Integer> firstToFourth = Set.of(1, 2, 3, 4); // 1-based index
        for (LocalDate d : datesOf(DayOfWeek.MONDAY, start, end)) {
            completeChecklistIndices(user1Id, LOOP_RUNNING, d, firstToFourth);
        }
        for (LocalDate d : datesOf(DayOfWeek.WEDNESDAY, start, end)) {
            completeChecklistIndices(user1Id, LOOP_RUNNING, d, firstToFourth);
        }
        // 2) 토익 공부하기 (12/1~12/24) - 월/수 모두 all complete
        LocalDate toeicEnd = LocalDate.of(2025, 12, 24);
        for (LocalDate d : datesOf(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), start, toeicEnd)) {
            completeAllChecklists(user1Id, LOOP_TOEIC, d);
        }
        // 3) 코딩 테스트 준비 (12/23~12/31) - 화요일 모두 all complete
        LocalDate codingStart = LocalDate.of(2025, 12, 23);
        LocalDate codingEnd = LocalDate.of(2025, 12, 31);
        for (LocalDate d : datesOf(DayOfWeek.TUESDAY, codingStart, codingEnd)) {
            completeAllChecklists(user1Id, LOOP_CODING, d);
        }
        log.info("[NotProd] completeScenario_2_1 done");
    }

    // [유저 1이 체크리스트 완료 처리(2)-2(버거운 루프 선정 검증)]
    // 0) 단일 루프 완료(체크리스트 완료)
    // 1) 동계 런닝 훈련: 1주(12/1~12/3)
    //    - MONDAY/WEDNESDAY: (모든 체크리스트 완료)
    //    - 마지막 all complete 대상 루프는 12/3(WEDNESDAY)
    // 2) 토익 공부하기: 5주(12/1~12/31)
    //    - MONDAY/WEDNESDAY: (1번 완료, 2/3/4/5번 미완료)
    @Transactional
    public void completeScenario_2_2() {
        Long user1Id = user1Id();
        // 0) 강릉 당일치기 단일 루프 체크리스트 완료
        completeAllChecklists(user1Id, LOOP_TRAVEL, LocalDate.of(2025, 12, 25));
        LocalDate start = LocalDate.of(2025, 12, 1);

        // 1) 동계 런닝 훈련: 12/1~12/8, 월/수 모두 all complete (마지막은 12/8 월)
        LocalDate runningEnd = LocalDate.of(2025, 12, 3);
        for (LocalDate d : datesOf(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), start, runningEnd)) {
            completeAllChecklists(user1Id, LOOP_RUNNING, d);
        }
        // 2) 토익 공부하기: 12/1~12/31 월/수 모두 1번만 완료
        LocalDate toeicEnd = LocalDate.of(2025, 12, 31);
        for (LocalDate d : datesOf(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), start, toeicEnd)) {
            completeChecklistIndices(user1Id, LOOP_TOEIC, d, Set.of(1)); // 1번만 완료
        }
        // 3) 코딩 테스트 준비 (12/23~12/31) - 화요일 모두 all complete
        LocalDate codingStart = LocalDate.of(2025, 12, 23);
        LocalDate codingEnd = LocalDate.of(2025, 12, 31);
        for (LocalDate d : datesOf(DayOfWeek.TUESDAY, codingStart, codingEnd)) {
            completeAllChecklists(user1Id, LOOP_CODING, d);
        }
        log.info("[NotProd] completeScenario_2_2 done");
    }

    // ----------------- 헬퍼 메서드 -----------------

    private String userEmail(int userNo) {
        return USER_EMAIL_FORMAT.formatted(userNo);
    }

    private CurrentUserDto userCurrentUser(int userNo) {
        String email = userEmail(userNo);
        Member user = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("user not found: " + email));
        return memberConverter.toCurrentUserDto(user);
    }

    private CurrentUserDto user1CurrentUser() {
        Member user1 = memberRepository.findByEmail(USER1_EMAIL)
                .orElseThrow(() -> new IllegalStateException("user1 not found: " + USER1_EMAIL));
        return memberConverter.toCurrentUserDto(user1);
    }

    private Long user1Id() {
        return user1CurrentUser().id();
    }

    private List<LocalDate> datesOf(DayOfWeek day, LocalDate start, LocalDate end) {
        return datesOf(Set.of(day), start, end);
    }

    private List<LocalDate> datesOf(Set<DayOfWeek> days, LocalDate start, LocalDate end) {
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (days.contains(d.getDayOfWeek())) {
                result.add(d);
            }
        }
        return result;
    }

    private void completeAllChecklists(Long memberId, String title, LocalDate date) {
        Loop loop = mustFindLoop(memberId, title, date);
        applyChecklistCompletion(loop, toIndexSet(loop.getLoopChecklists().size())); // all
    }

    private void completeChecklistIndices(Long memberId, String title, LocalDate date, Set<Integer> completedIndices1Based) {
        Loop loop = mustFindLoop(memberId, title, date);
        applyChecklistCompletion(loop, completedIndices1Based);
    }

    private void applyChecklistCompletion(Loop loop, Set<Integer> completedIndices1Based) {
        List<LoopChecklist> sorted = loop.getLoopChecklists().stream()
                .sorted(Comparator.comparing(LoopChecklist::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        for (int i = 0; i < sorted.size(); i++) {
            int idx1 = i + 1; // 1-based
            LoopChecklist cl = sorted.get(i);
            boolean done = completedIndices1Based.contains(idx1);
            cl.setCompleted(done);
        }
        boolean allDone = sorted.stream().allMatch(c -> Boolean.TRUE.equals(c.getCompleted()));
        loop.setCompleted(allDone);

        // dirty-checking으로 flush됨 (필요하면 명시 save)
        loopRepository.save(loop);
    }

    private Set<Integer> toIndexSet(int n) {
        Set<Integer> s = new HashSet<>();
        for (int i = 1; i <= n; i++) s.add(i);
        return s;
    }

    private Loop mustFindLoop(Long memberId, String title, LocalDate date) {
        return loopRepository.findFirstByMember_IdAndTitleAndLoopDate(memberId, title, date)
                .orElseThrow(() -> new IllegalStateException(
                        "Loop not found. memberId=" + memberId + ", title=" + title + ", date=" + date
                ));
    }
}
