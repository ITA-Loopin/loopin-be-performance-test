package com.loopone.loopinbe.domain.loop.loopReport.serviceImpl;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopDetailResponse;
import com.loopone.loopinbe.domain.loop.loop.entity.Loop;
import com.loopone.loopinbe.domain.loop.loop.entity.LoopRule;
import com.loopone.loopinbe.domain.loop.loop.mapper.LoopMapper;
import com.loopone.loopinbe.domain.loop.loop.repository.LoopRepository;
import com.loopone.loopinbe.domain.loop.loopChecklist.entity.LoopChecklist;
import com.loopone.loopinbe.domain.loop.loopReport.dto.MonthReportDto;
import com.loopone.loopinbe.domain.loop.loopReport.dto.ProgressLoopDto;
import com.loopone.loopinbe.domain.loop.loopReport.dto.WeekReportDto;
import com.loopone.loopinbe.domain.loop.loopReport.dto.res.LoopReportResponse;
import com.loopone.loopinbe.domain.loop.loopReport.enums.DetailReportState;
import com.loopone.loopinbe.domain.loop.loopReport.enums.ReportState;
import com.loopone.loopinbe.domain.loop.loopReport.messages.MonthReportMessages;
import com.loopone.loopinbe.domain.loop.loopReport.messages.ReportMessages;
import com.loopone.loopinbe.domain.loop.loopReport.messages.WeekReportMessages;
import com.loopone.loopinbe.domain.loop.loopReport.service.LoopReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoopReportServiceImpl implements LoopReportService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MIN_MONTH_RULE_DAYS = 3;
    private final LoopRepository loopRepository;
    private final LoopMapper loopMapper;

    // 루프 리포트 조회
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "loopReport", key = "#currentUser.id()")
    public LoopReportResponse getLoopReport(CurrentUserDto currentUser) {
        Long memberId = currentUser.id();
        LocalDate today = LocalDate.now();
        String userName = currentUser.nickname();

        // ---- 최근 10일(오늘 제외): [today-10, today-1]
        LocalDate tenStart = today.minusDays(10);
        LocalDate tenEnd = today.minusDays(1);
        List<Loop> tenDayLoops = loopRepository.findRepeatLoopsByMemberAndDateBetween(memberId, tenStart, tenEnd);
        Long tenDayAvgPercent = calcAverageAchievePercentOrNull(tenDayLoops);
        ReportState loopReportState = getReportState(tenDayAvgPercent);
        String reportStateMessage = getReportStateMessage(loopReportState, userName);

        // ---- 최근 7일(오늘 제외): [today-7, today-1]
        LocalDate sevenStart = today.minusDays(7);
        LocalDate sevenEnd = today.minusDays(1);
        List<Loop> sevenDayLoops = loopRepository.findRepeatLoopsByMemberAndDateBetween(memberId, sevenStart, sevenEnd);
        Long sevenDayTotalCount = (long) sevenDayLoops.size();
        Long sevenDayDoneCount = sevenDayLoops.stream()
                .map(this::calcLoopAchievePercent)
                .filter(p -> p >= 50L)
                .count();
        WeekReportDto weekReportDto = buildWeekReportDto(sevenDayLoops);

        // ---- 이번달: [monthStart, monthEnd]
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        List<Loop> monthLoops = loopRepository.findRepeatLoopsByMemberAndDateBetween(memberId, monthStart, monthEnd);
        MonthReportDto monthReportDto = buildMonthReportDto(monthLoops);
        return new LoopReportResponse(
                loopReportState,
                reportStateMessage,
                sevenDayDoneCount,
                sevenDayTotalCount,
                tenDayAvgPercent,
                weekReportDto,
                monthReportDto
        );
    }

    // ----------------- weekReportDto 메서드 -----------------

    // weekReportDto 생성 로직
    private WeekReportDto buildWeekReportDto(List<Loop> sevenDayLoops) {
        // weekAvgPercent: 오늘 제외 최근 7일간 "모든 루프" 평균 달성률 (없으면 null)
        Long weekAvgPercent = calcAverageAchievePercentOrNull(sevenDayLoops);
        // weekCard: 루프 있으면 날짜별 평균 달성률로 채움
        Map<LocalDate, Long> weekCard = buildCard(sevenDayLoops);

        // 잘한 루프 / 버거운 루프 선정
        ProgressLoopDto good = pickGoodProgressLoopDto(sevenDayLoops);
        ProgressLoopDto bad = pickBadProgressLoopDto(sevenDayLoops);

        DetailReportState detailReportState = resolveDetailReportState(good, bad);
        DetailReportStateMessage msg = getWeekDetailReportStateMessage(detailReportState);

        // 상태별 fallback 메시지 주입 (loop=null인 쪽만 채움)
        if (good == null && msg.goodFallback() != null) {
            good = new ProgressLoopDto(null, null, null, msg.goodFallback());
        }
        if (bad == null && msg.badFallback() != null) {
            bad = new ProgressLoopDto(null, null, null, msg.badFallback());
        }
        return new WeekReportDto(detailReportState, weekAvgPercent, weekCard, good, bad);
    }

    // 상태별 fallback 메시지 매핑 (loop=null인 쪽만 채움)
    private DetailReportStateMessage getWeekDetailReportStateMessage(DetailReportState state) {
        return switch (state) {
            case BOTH_EXIST -> new DetailReportStateMessage(null, null);
            case ONLY_GOOD -> new DetailReportStateMessage(null, WeekReportMessages.ONLY_GOOD);
            case ONLY_BAD -> new DetailReportStateMessage(WeekReportMessages.ONLY_BAD, null);
            case NONE -> new DetailReportStateMessage(WeekReportMessages.NONE, WeekReportMessages.NONE);
        };
    }

    // ----------------- monthReportDto 메서드 -----------------

    // monthReportDto 생성 로직
    private MonthReportDto buildMonthReportDto(List<Loop> monthLoops) {
        // monthCard: 루프 있으면 날짜별 평균 달성률로 채움
        Map<LocalDate, Long> monthCard = buildCard(monthLoops);

        // month에서 "잘한/버거운 반복루프" 후보만 필터링
        List<Loop> eligibleMonthLoops = filterRulesByMinScheduledDays(monthLoops, MIN_MONTH_RULE_DAYS);

        // 잘한/버거운 루프 선정
        ProgressLoopDto good = pickGoodProgressLoopDto(eligibleMonthLoops);
        ProgressLoopDto bad = pickBadProgressLoopDto(eligibleMonthLoops);

        DetailReportState detailReportState = resolveDetailReportState(good, bad);
        DetailReportStateMessage msg = getMonthDetailReportStateMessage(detailReportState);

        // 상태별 fallback 메시지 주입 (요구사항)
        if (good == null && msg.goodFallback() != null) {
            good = new ProgressLoopDto(null, null, null, msg.goodFallback());
        }
        if (bad == null && msg.badFallback() != null) {
            bad = new ProgressLoopDto(null, null, null, msg.badFallback());
        }
        return new MonthReportDto(detailReportState, monthCard, good, bad);
    }

    // 상태별 fallback 메시지 매핑 (loop=null인 쪽만 채움)
    private DetailReportStateMessage getMonthDetailReportStateMessage(DetailReportState state) {
        return switch (state) {
            case BOTH_EXIST -> new DetailReportStateMessage(null, null);
            case ONLY_GOOD -> new DetailReportStateMessage(null, MonthReportMessages.ONLY_GOOD);
            case ONLY_BAD -> new DetailReportStateMessage(MonthReportMessages.ONLY_BAD, null);
            case NONE -> new DetailReportStateMessage(MonthReportMessages.NONE, MonthReportMessages.NONE);
        };
    }

    // 반복루프(ruleId)별로 "해당 기간에 존재하는 loopDate(중복 제거)" 개수가 minDays 미만이면 제외
    // - 예) 12/23, 12/30 두 번만 있는 WEEKLY[TUE]면 distinct loopDate = 2 -> 제외
    private List<Loop> filterRulesByMinScheduledDays(List<Loop> loops, int minDays) {
        if (loops == null || loops.isEmpty()) return List.of();
        Map<Long, List<Loop>> byRule = loops.stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getLoopRule() != null && l.getLoopRule().getId() != null)
                .collect(Collectors.groupingBy(l -> l.getLoopRule().getId()));
        return byRule.values().stream()
                .filter(ruleLoops -> ruleLoops.stream()
                        .map(Loop::getLoopDate)
                        .filter(Objects::nonNull)
                        .distinct()
                        .count() >= minDays
                )
                .flatMap(List::stream)
                .toList();
    }

    // ----------------- 헬퍼 메서드 -----------------

    // week/month Card 생성 로직
    private Map<LocalDate, Long> buildCard(List<Loop> loops) {
        if (loops == null || loops.isEmpty()) return new TreeMap<>();
        return loops.stream()
            .filter(Objects::nonNull)
            .filter(l -> l.getLoopDate() != null)
            .collect(Collectors.groupingBy(
                Loop::getLoopDate,
                TreeMap::new,
                Collectors.collectingAndThen(
                    Collectors.averagingLong(this::calcLoopAchievePercent),
                    avg -> Math.round(avg)
                )
            ));
    }

    // 잘한 루프 선정 로직
    private ProgressLoopDto pickGoodProgressLoopDto(List<Loop> loops) {
        if (loops == null || loops.isEmpty()) return null;

        Map<Long, List<Loop>> byRule = loops.stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getLoopRule() != null && l.getLoopRule().getId() != null)
                .collect(Collectors.groupingBy(l -> l.getLoopRule().getId()));
        List<GoodRuleAgg> candidates = new ArrayList<>();

        for (Map.Entry<Long, List<Loop>> entry : byRule.entrySet()) {
            List<Loop> ruleLoops = entry.getValue();
            if (ruleLoops == null || ruleLoops.isEmpty()) continue;

            // 반복루프 평균 달성률
            long avgAchieve = Math.round(
                    ruleLoops.stream().mapToLong(this::calcLoopAchievePercent).average().orElse(0.0)
            );

            // 조건: 평균 70% 이상인 반복루프만 후보
            if (avgAchieve < 70L) continue;

            // 2순위: 100% 개수(=완벽 달성 일수)
            long fullDays = ruleLoops.stream()
                    .mapToLong(this::calcLoopAchievePercent)
                    .filter(p -> p == 100L)
                    .count();

            // tie-breaker용: 최근 실행일, 생성일(오래된 것 우선)
            LocalDate lastExecutionDate = ruleLoops.stream()
                    .map(Loop::getLoopDate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDate.MIN);

            Instant createdAt = ruleLoops.stream()
                    .map(this::extractCreatedAtForLoopNullableSafe)
                    .min(Comparator.naturalOrder())
                    .orElse(Instant.EPOCH);

            candidates.add(new GoodRuleAgg(
                    entry.getKey(),
                    avgAchieve,
                    fullDays,
                    lastExecutionDate,
                    createdAt
            ));
        }
        if (candidates.isEmpty()) return null;

        GoodRuleAgg best = candidates.stream()
                .sorted(
                        Comparator.comparingLong(GoodRuleAgg::avgAchieve).reversed() // 1) 평균 달성률 높은 순
                                .thenComparing(Comparator.comparingLong(GoodRuleAgg::fullAchieveDays).reversed()) // 2) 100% 일수 많은 순
                                .thenComparing(GoodRuleAgg::lastExecutionDate, Comparator.reverseOrder()) // 3) 최근 실행일
                                .thenComparing(GoodRuleAgg::createdAt) // 4) 오래된 생성일 우선
                )
                .findFirst()
                .orElseThrow();
        List<Loop> chosenRuleLoops = byRule.get(best.ruleId());
        if (chosenRuleLoops == null || chosenRuleLoops.isEmpty()) return null;

        // 대표 loop 선택: 가능하면 "가장 최근 100% loop" -> 없으면 "가장 최근 loop"
        Loop chosenLoop = chosenRuleLoops.stream()
                .filter(l -> calcLoopAchievePercent(l) == 100L)
                .filter(l -> l.getLoopDate() != null)
                .sorted(Comparator.comparing(Loop::getLoopDate, Comparator.reverseOrder())
                        .thenComparing(this::extractCreatedAtForLoopNullableSafe))
                .findFirst()
                .orElseGet(() -> chosenRuleLoops.stream()
                        .filter(l -> l.getLoopDate() != null)
                        .sorted(Comparator.comparing(Loop::getLoopDate, Comparator.reverseOrder())
                                .thenComparing(this::extractCreatedAtForLoopNullableSafe))
                        .findFirst()
                        .orElse(null));
        if (chosenLoop == null) return null;

        return new ProgressLoopDto(
                chosenLoop.getTitle(),
                loopMapper.loopRuleToLoopRuleDTO(chosenLoop.getLoopRule()),
                best.avgAchieve(), // 반복루프 평균 달성률
                null
        );
    }

    // 버거운 루프 선정 로직
    private ProgressLoopDto pickBadProgressLoopDto(List<Loop> loops) {
        if (loops == null || loops.isEmpty()) return null;

        Map<Long, List<Loop>> byRule = loops.stream()
                .filter(Objects::nonNull)
                .filter(l -> l.getLoopRule() != null && l.getLoopRule().getId() != null)
                .collect(Collectors.groupingBy(l -> l.getLoopRule().getId()));
        List<BadRuleAgg> candidates = new ArrayList<>();

        for (Map.Entry<Long, List<Loop>> entry : byRule.entrySet()) {
            List<Loop> ruleLoops = entry.getValue();
            if (ruleLoops == null || ruleLoops.isEmpty()) continue;

            // 반복루프 평균 달성률
            long avgAchieve = Math.round(
                    ruleLoops.stream().mapToLong(this::calcLoopAchievePercent).average().orElse(0.0)
            );

            // 조건: 평균 50% 미만인 반복루프만 후보(버거운 기준)
            if (avgAchieve >= 50L) continue;

            // 2순위: 0% 개수(=완전 미달성 일수)
            long zeroDays = ruleLoops.stream()
                    .mapToLong(this::calcLoopAchievePercent)
                    .filter(p -> p == 0L)
                    .count();

            // tie-breaker용: 평균 더 낮은 순, 최근 실행일, 생성일
            LocalDate lastExecutionDate = ruleLoops.stream()
                    .map(Loop::getLoopDate)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDate.MIN);

            Instant createdAt = ruleLoops.stream()
                    .map(this::extractCreatedAtForLoopNullableSafe)
                    .min(Comparator.naturalOrder())
                    .orElse(Instant.EPOCH);

            candidates.add(new BadRuleAgg(
                    entry.getKey(),
                    avgAchieve,
                    zeroDays,
                    lastExecutionDate,
                    createdAt
            ));
        }
        if (candidates.isEmpty()) return null;

        BadRuleAgg worst = candidates.stream()
                .sorted(
                        Comparator.comparingLong(BadRuleAgg::avgAchieve) // 1) 평균 달성률 낮은 순
                                .thenComparing(Comparator.comparingLong(BadRuleAgg::zeroDays).reversed()) // 2) 0% 일수 많은 순
                                .thenComparing(BadRuleAgg::lastExecutionDate) // 3) 오래된 실행일 우선
                                .thenComparing(BadRuleAgg::createdAt) // 4) 오래된 생성일 우선
                )
                .findFirst()
                .orElseThrow();

        List<Loop> chosenRuleLoops = byRule.get(worst.ruleId());
        if (chosenRuleLoops == null || chosenRuleLoops.isEmpty()) return null;

        // 대표 loop 선택: 가능하면 "가장 최근 0% loop" -> 없으면 "가장 최근 loop"
        Loop chosenLoop = chosenRuleLoops.stream()
                .filter(l -> calcLoopAchievePercent(l) == 0L)
                .filter(l -> l.getLoopDate() != null)
                .sorted(Comparator.comparing(Loop::getLoopDate, Comparator.reverseOrder())
                        .thenComparing(this::extractCreatedAtForLoopNullableSafe))
                .findFirst()
                .orElseGet(() -> chosenRuleLoops.stream()
                        .filter(l -> l.getLoopDate() != null)
                        .sorted(Comparator.comparing(Loop::getLoopDate, Comparator.reverseOrder())
                                .thenComparing(this::extractCreatedAtForLoopNullableSafe))
                        .findFirst()
                        .orElse(null));
        if (chosenLoop == null) return null;

        return new ProgressLoopDto(
                chosenLoop.getTitle(),
                loopMapper.loopRuleToLoopRuleDTO(chosenLoop.getLoopRule()),
                worst.avgAchieve(), // 반복루프 평균 달성률
                null
        );
    }

    // 루프 리스트에 루프 존재하면 평균 달성률 계산, 없으면 null
    private Long calcAverageAchievePercentOrNull(List<Loop> loops) {
        if (loops == null || loops.isEmpty()) return null;
        double avg = loops.stream()
                .mapToLong(this::calcLoopAchievePercent)
                .average()
                .orElse(0.0);
        return Math.round(avg);
    }

    // 루프 달성률 계산 규칙
    // - 체크리스트가 있으면: (완료 체크리스트 수 / 전체 체크리스트 수) * 100
    // - 체크리스트가 없으면: loop.completed == true ? 100 : 0
    private Long calcLoopAchievePercent(Loop loop) {
        List<LoopChecklist> items = loop.getLoopChecklists();
        if (items == null || items.isEmpty()) {
            return loop.isCompleted() ? 100L : 0L;
        }
        long total = items.size();
        long done = items.stream().filter(LoopChecklist::getCompleted).count();
        return Math.round((done * 100.0) / total);
    }

    // tenDayAvgPercent 범위에 따른 루프리포트 상태 매핑
    private ReportState getReportState(Long tenDayAvgPercent) {
        if (tenDayAvgPercent == null) return ReportState.NONE;
        if (tenDayAvgPercent >= 80L) return ReportState.GOOD;
        if (tenDayAvgPercent >= 50L) return ReportState.OK;
        return ReportState.HARD;
    }

    // 루프리포트 상태에 따른 메시지 매핑
    private String getReportStateMessage(ReportState state, String userName) {
        String base = switch (state) {
            case GOOD -> ReportMessages.GOOD;
            case OK -> ReportMessages.OK;
            case HARD -> ReportMessages.HARD;
            case NONE -> ReportMessages.NONE;
        };
        return applyUserName(base, userName);
    }

    private String applyUserName(String message, String userName) {
        if (message == null) return null;
        if (userName == null || userName.isBlank()) return message; // 이름 없으면 원본 유지
        return message.replace("루핑이", userName); // "루핑이"만 사용자명으로 치환
    }

    // DetailReportState 매핑 로직
    private DetailReportState resolveDetailReportState(ProgressLoopDto good, ProgressLoopDto bad) {
        boolean hasGood = good != null;
        boolean hasBad = bad != null;

        if (hasGood && hasBad) return DetailReportState.BOTH_EXIST;
        if (hasGood) return DetailReportState.ONLY_GOOD;
        if (hasBad) return DetailReportState.ONLY_BAD;
        return DetailReportState.NONE;
    }

    private record DetailReportStateMessage(String goodFallback, String badFallback) {}

    // ----------------- tie-breaker용 내부 유틸 -----------------

    // 루프 생성일 추출
    private Instant extractCreatedAtForLoopNullableSafe(Loop loop) {
        try {
            Instant createdAt = loop.getCreatedAt(); // createdAt이 LocalDateTime이라면
            return createdAt == null ? Instant.EPOCH : createdAt.atZone(KST).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    // ----------------- 집계용 내부 record -----------------

    private record GoodRuleAgg(
            Long ruleId,
            long avgAchieve,
            long fullAchieveDays,
            LocalDate lastExecutionDate,
            Instant createdAt
    ) {}

    private record BadRuleAgg(
            Long ruleId,
            long avgAchieve,
            long zeroDays,
            LocalDate lastExecutionDate,
            Instant createdAt
    ) {}
}
