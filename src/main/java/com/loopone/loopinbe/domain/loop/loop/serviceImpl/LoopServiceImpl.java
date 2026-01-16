package com.loopone.loopinbe.domain.loop.loop.serviceImpl;

import com.loopone.loopinbe.domain.account.auth.currentUser.CurrentUserDto;
import com.loopone.loopinbe.domain.account.member.converter.MemberConverter;
import com.loopone.loopinbe.domain.chat.chatMessage.dto.req.ChatMessageRequest;
import com.loopone.loopinbe.domain.chat.chatMessage.entity.type.MessageType;
import com.loopone.loopinbe.domain.chat.chatMessage.service.ChatMessageService;
import com.loopone.loopinbe.domain.chat.chatRoom.entity.ChatRoom;
import com.loopone.loopinbe.domain.chat.chatRoom.repository.ChatRoomRepository;
import com.loopone.loopinbe.domain.chat.chatRoom.service.ChatRoomStateService;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCompletionUpdateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopCreateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopGroupUpdateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.req.LoopUpdateRequest;
import com.loopone.loopinbe.domain.loop.loop.dto.res.DailyLoopsResponse;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopCalendarResponse;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopDetailResponse;
import com.loopone.loopinbe.domain.loop.loop.entity.Loop;
import com.loopone.loopinbe.domain.loop.loop.entity.LoopPage;
import com.loopone.loopinbe.domain.loop.loop.entity.LoopRule;
import com.loopone.loopinbe.domain.loop.loop.enums.RepeatType;
import com.loopone.loopinbe.domain.loop.loop.mapper.LoopMapper;
import com.loopone.loopinbe.domain.loop.loop.repository.LoopRepository;
import com.loopone.loopinbe.domain.loop.loop.repository.LoopRuleRepository;
import com.loopone.loopinbe.domain.loop.loop.service.LoopService;
import com.loopone.loopinbe.domain.loop.loopChecklist.entity.LoopChecklist;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static com.loopone.loopinbe.global.constants.Constant.AI_AFTER_SELECT_LOOP_MESSAGE;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoopServiceImpl implements LoopService {
    private final LoopRepository loopRepository;
    private final LoopRuleRepository loopRuleRepository;
    private final LoopMapper loopMapper;
    private final MemberConverter memberConverter;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageService chatMessageService;
    private final CacheManager cacheManager;
    private final ChatRoomStateService chatRoomStateService;

    // 루프 생성
    @Override
    @Transactional
    public Long createLoop(LoopCreateRequest requestDTO, CurrentUserDto currentUser) {

        List<Loop> createdLoops;
        LoopRule loopRule = null;

        switch (requestDTO.scheduleType()) {
            case NONE -> createdLoops = List.of(createSingleLoop(requestDTO, currentUser));

            case WEEKLY -> {
                loopRule = createLoopRule(requestDTO, currentUser);
                createdLoops = createWeeklyLoops(requestDTO, currentUser, loopRule);
            }

            case MONTHLY -> {
                loopRule = createLoopRule(requestDTO, currentUser);
                createdLoops = createMonthlyLoops(requestDTO, currentUser, loopRule);
            }

            case YEARLY -> {
                loopRule = createLoopRule(requestDTO, currentUser);
                createdLoops = createYearlyLoops(requestDTO, currentUser, loopRule);
            }

            default -> throw new ServiceException(ReturnCode.UNKNOWN_SCHEDULE_TYPE);
        }

        Long loopId = createdLoops.isEmpty() ? null : createdLoops.get(0).getId();

        if (requestDTO.chatRoomId() != null && loopId != null) {
            Loop loop = loopRepository.findById(loopId)
                    .orElseThrow(() -> new ServiceException(ReturnCode.LOOP_NOT_FOUND));
            ChatRoom chatRoom = linkLoopToChatRoom(requestDTO.chatRoomId(), loop);

            if (chatRoom.isBotRoom()) {
                chatMessageService.sendChatMessage(
                        chatRoom.getId(),
                        new ChatMessageRequest(
                                AI_AFTER_SELECT_LOOP_MESSAGE,
                                UUID.randomUUID(),
                                MessageType.GET_LOOP
                        ),
                        currentUser
                );
            }
        }

        // 커밋 후 캐시 무효화
        Set<Long> loopIds = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        Set<YearMonth> yms = new HashSet<>();

        for (Loop l : createdLoops) {
            if (l == null) continue;
            if (l.getId() != null) loopIds.add(l.getId());
            LocalDate d = l.getLoopDate();
            if (d != null) {
                dates.add(d);
                yms.add(YearMonth.from(d));
            }
        }
        evictLoopCachesAfterCommit(currentUser.id(), loopIds, dates, yms);

        return loopId;
    }

    // 루프 상세 조회
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "loopDetail", key = "#currentUser.id() + ':' + #loopId")
    public LoopDetailResponse getDetailLoop(Long loopId, CurrentUserDto currentUser) {
        // 루프 조회
        Loop loop = loopRepository.findById(loopId).orElseThrow(() -> new ServiceException(ReturnCode.LOOP_NOT_FOUND));

        // 루프 검증
        validateLoopOwner(loop, currentUser);

        return loopMapper.toDetailResponse(loop);
    }

    // 날짜별 루프 리스트 조회
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "dailyLoops", key = "#currentUser.id() + ':' + #date")
    public DailyLoopsResponse getDailyLoops(LocalDate date, CurrentUserDto currentUser) {
        // 루프 리스트 조회
        List<Loop> DailyLoops = loopRepository.findByMemberIdAndLoopDate(currentUser.id(), date);

        return loopMapper.toDailyLoopsResponse(DailyLoops);
    }

/*    //루프 전체 리스트 조회
    @Override
    @Transactional(readOnly = true)
    public PageResponse<LoopSimpleResponse> getAllLoop(Pageable pageable, CurrentUserDto currentUser) {
        checkPageSize(pageable.getPageSize());

        //Loop 엔티티 페이지를 DB에서 조회
        Page<Loop> loopPage = loopRepository.findByMemberIdWithOrder(currentUser.id(), pageable);
        List<Long> loopIds = loopPage.stream().map(Loop::getId).toList();

        //모든 체크리스트를 한 번에 조회해서 Map으로 그룹핑
        Map<Long, List<LoopChecklist>> checklistsMap = loopChecklistRepository.findByLoopIdIn(loopIds)
                .stream()
                .collect(Collectors.groupingBy(cl -> cl.getLoop().getId())); // Stream의 groupingBy를 사용해 한 줄로 그룹핑

        //엔티티 페이지를 DTO 페이지로 직접 변환
        Page<LoopSimpleResponse> simpleDtoPage = loopPage.map(loopMapper::toSimpleResponse);

        return PageResponse.of(simpleDtoPage);
    }*/

    // 루프 완료 처리
    @Override
    @Transactional
    public void updateLoopCompletion(Long loopId, LoopCompletionUpdateRequest requestDTO, CurrentUserDto currentUser) {
        // 루프 조회
        Loop loop = loopRepository.findById(loopId).orElseThrow(() -> new ServiceException(ReturnCode.LOOP_NOT_FOUND));

        // 루프 검증
        validateLoopOwner(loop, currentUser);
        LocalDate loopDate = loop.getLoopDate();
        YearMonth ym = YearMonth.from(loopDate);

        // 루프 완료 상태 변경
        loop.setCompleted(requestDTO.completed());

        //체크리스트가 있는 경우 체크리스트의 완료 상태 변경
        if (!loop.getLoopChecklists().isEmpty()) {
            loop.getLoopChecklists().forEach(loopChecklist ->
                    loopChecklist.setCompleted(requestDTO.completed())
            );
        }
        // 커밋 후 캐시 무효화
        evictLoopCachesAfterCommit(currentUser.id(), List.of(loopId), List.of(loopDate), List.of(ym));
    }

    // 단일 루프 수정
    @Override
    @Transactional
    public void updateLoop(Long loopId, LoopUpdateRequest requestDTO, CurrentUserDto currentUser) {
        // 루프 조회
        Loop loop = loopRepository.findById(loopId).orElseThrow(() -> new ServiceException(ReturnCode.LOOP_NOT_FOUND));
        LocalDate oldDate = loop.getLoopDate();

        // 루프 검증
        validateLoopOwner(loop, currentUser);

        // LoopRule 연결 해제 (단일 수정을 하는 경우, 독립적인 루프가 되기에)
        loop.setLoopRule(null);

        //루프 정보 수정
        if (requestDTO.title() != null) loop.setTitle(requestDTO.title());
        if (requestDTO.content() != null) loop.setContent(requestDTO.content());
        if (requestDTO.specificDate() != null) loop.setLoopDate(requestDTO.specificDate());

        // 체크리스트 수정
        if (requestDTO.checklists() != null) {
            loop.getLoopChecklists().clear(); // 기존 목록 삭제
            if (!requestDTO.checklists().isEmpty()) {
                for (String cl : requestDTO.checklists()) {
                    loop.addChecklist(LoopChecklist.builder().content(cl).build());
                }
            }
        }
        // 커밋 후 캐시 무효화
        LocalDate newDate = loop.getLoopDate();
        Set<LocalDate> dates = new HashSet<>();
        dates.add(oldDate);
        dates.add(newDate);

        Set<YearMonth> yms = new HashSet<>();
        if (oldDate != null) yms.add(YearMonth.from(oldDate));
        if (newDate != null) yms.add(YearMonth.from(newDate));

        evictLoopCachesAfterCommit(currentUser.id(), List.of(loopId), dates, yms);
    }

    // 루프 그룹 전체 수정
    @Override
    @Transactional
    public void updateLoopGroup(Long loopRuleId, LoopGroupUpdateRequest requestDTO, CurrentUserDto currentUser) {
        // 루프 조회
        LoopRule loopRule = loopRuleRepository.findById(loopRuleId)
                .orElseThrow(() -> new ServiceException(ReturnCode.LOOP_RULE_NOT_FOUND));

        // loopRule 검증
        validateLoopRuleOwner(loopRule, currentUser);

        ChatRoom chatRoom = chatRoomRepository.findByLoopRuleId(loopRule.getId());
        chatRoom.setLoop(null);

        List<Loop> oldLoops = findAllByLoopRule(loopRule, LocalDate.now());

        // 무효화 대상 수집 (old)
        Set<Long> loopIds = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        Set<YearMonth> yms = new HashSet<>();
        for (Loop l : oldLoops) {
            loopIds.add(l.getId());
            LocalDate d = l.getLoopDate();
            dates.add(d);
            if (d != null) yms.add(YearMonth.from(d));
        }
        loopRepository.deleteAll(oldLoops);

        // 새로운 규칙으로 생성
        LoopCreateRequest createRequestDTO = loopMapper.toLoopCreateRequest(requestDTO);
        List<Loop> newLoops = createUpdateLoop(createRequestDTO, loopRule, currentUser);

        // 무효화 대상 수집 (new)
        for (Loop l : newLoops) {
            loopIds.add(l.getId());
            LocalDate d = l.getLoopDate();
            dates.add(d);
            if (d != null) yms.add(YearMonth.from(d));
        }

        if (!newLoops.isEmpty()) {
            chatRoom.setLoop(newLoops.get(0));
            chatMessageService.sendChatMessage(
                    chatRoom.getId(),
                    new ChatMessageRequest(
                            AI_AFTER_SELECT_LOOP_MESSAGE,
                            UUID.randomUUID(),
                            MessageType.GET_LOOP
                    ),
                    currentUser
            );
            chatRoomStateService.setCallUpdateLoop(chatRoom.getId(), false);
        }
        // 커밋 후 캐시 무효화
        evictLoopCachesAfterCommit(currentUser.id(), loopIds, dates, yms);
    }

    // 단일 루프 삭제
    @Override
    @Transactional
    public void deleteLoop(Long loopId, CurrentUserDto currentUser) {
        // 루프 조회
        Loop loop = loopRepository.findById(loopId).orElseThrow(() -> new ServiceException(ReturnCode.LOOP_NOT_FOUND));

        // 루프 검증
        validateLoopOwner(loop, currentUser);

        LocalDate date = loop.getLoopDate();
        YearMonth ym = (date == null) ? null : YearMonth.from(date);

        // 채팅방 연결 해제
        chatRoomRepository.unlinkLoop(loopId);
        loopRepository.delete(loop);

        // 커밋 후 캐시 무효화
        evictLoopCachesAfterCommit(currentUser.id(), List.of(loopId), date == null ? List.of() : List.of(date), ym == null ? List.of() : List.of(ym));
    }

    // 루프 그룹 전체 삭제
    @Override
    @Transactional
    public void deleteLoopGroup(Long loopId, CurrentUserDto currentUser) {
        // 루프 조회
        Loop selectedLoop = loopRepository.findById(loopId)
                .orElseThrow(() -> new ServiceException(ReturnCode.LOOP_NOT_FOUND));
        LoopRule loopRule = selectedLoop.getLoopRule();

        // 그룹 루프가 아닌 경우 예외 처리
        if (loopRule == null) {
            LocalDate d = selectedLoop.getLoopDate();
            YearMonth ym = (d == null) ? null : YearMonth.from(d);

            // 채팅방 연결 해제
            chatRoomRepository.unlinkLoop(selectedLoop.getId());
            loopRepository.delete(selectedLoop);

            evictLoopCachesAfterCommit(
                    currentUser.id(),
                    List.of(selectedLoop.getId()),
                    d == null ? List.of() : List.of(d),
                    ym == null ? List.of() : List.of(ym)
            );
            return;
        }

        // loopRule 검증
        validateLoopRuleOwner(loopRule, currentUser);
        LocalDate targetDate = selectedLoop.getLoopDate();

        // 무효화 대상 수집
        Set<Long> loopIds = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        Set<YearMonth> yms = new HashSet<>();

        // 미래(선택일 포함) 삭제 대상
        List<Loop> futureLoops = findAllByLoopRule(loopRule, targetDate);
        for (Loop l : futureLoops) {
            loopIds.add(l.getId());
            LocalDate d = l.getLoopDate();
            dates.add(d);
            if (d != null) yms.add(YearMonth.from(d));
        }

        // 과거 루프는 loopRule 끊기므로 DTO/상세/일자 캐시도 갱신 필요
        List<Loop> pastLoops = findAllByLoopRulePast(loopRule, targetDate);
        for (Loop l : pastLoops) {
            loopIds.add(l.getId());
            LocalDate d = l.getLoopDate();
            dates.add(d);
            if (d != null) yms.add(YearMonth.from(d));
        }

        // 채팅방 연결 해제
        List<Long> futureLoopIds = futureLoops.stream().map(Loop::getId).toList();
        if (!futureLoopIds.isEmpty()) {
            chatRoomRepository.unlinkLoops(futureLoopIds);
        }

        // 해당 루프 리스트 삭제
        loopRepository.deleteAll(futureLoops);

        // 과거 루프는 연결 끊기
        pastLoops.forEach(l -> l.setLoopRule(null));

        // loopRule 삭제 (자식이 없기에 삭제 가능)
        loopRuleRepository.delete(loopRule);

        // 커밋 후 loopReport 캐시 무효화
        evictLoopCachesAfterCommit(currentUser.id(), loopIds, dates, yms);
    }

    // 사용자가 생성한 루프 전체 삭제
    @Override
    @Transactional
    public void deleteMyLoops(Long memberId) {
        // 1) Loop 먼저 전부 삭제 (LoopChecklist는 cascade로 같이 삭제됨)
        List<Loop> loops = loopRepository.findAllByMemberId(memberId);
        // 무효화 대상 수집 (삭제 전)
        Set<Long> loopIds = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        Set<YearMonth> yms = new HashSet<>();
        for (Loop l : loops) {
            loopIds.add(l.getId());
            LocalDate d = l.getLoopDate();
            dates.add(d);
            if (d != null) yms.add(YearMonth.from(d));
        }
        if (!loops.isEmpty()) {
            List<Long> ids = loops.stream().map(Loop::getId).toList();
            chatRoomRepository.unlinkLoops(ids);
            loopRepository.deleteAll(loops);
        }
        // 2) TeamLoop가 참조하지 않는 개인 LoopRule만 삭제
        loopRuleRepository.deletePersonalRulesNotUsedAnywhere(memberId);

        // 커밋 후 loopReport 캐시 무효화
        evictLoopCachesAfterCommit(memberId, loopIds, dates, yms);
    }

    //루프 캘린더 조회
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "loopCalendar", key = "#currentUser.id() + ':' + #year + ':' + #month")
    public LoopCalendarResponse getLoopCalendar(int year, int month, CurrentUserDto currentUser) {
        YearMonth targetYearMonth = YearMonth.of(year, month);

        // 조회 범위 계산
        LocalDate startDate = targetYearMonth.atDay(1).minusDays(7); // 전월 마지막 주 포함
        LocalDate endDate = targetYearMonth.atEndOfMonth().plusDays(7); // 익월 첫 주 포함

        // 해당 기간 내 개인 루프가 존재하는 날짜들만 조회
        List<LocalDate> existingLoopDates = loopRepository.findLoopDatesByMemberIdAndDateRange(
                currentUser.id(), startDate, endDate
        );

        // 빠른 조회를 위해 Set으로 변환
        Set<LocalDate> hasLoopDateSet = new HashSet<>(existingLoopDates);

        // 시작일부터 종료일까지 하루씩 순회하며 결과 리스트 생성
        List<LoopCalendarResponse.CalendarDay> calendarDays = new ArrayList<>();
        startDate.datesUntil(endDate.plusDays(1)).forEach(currentDate -> {
            boolean hasLoop = hasLoopDateSet.contains(currentDate);
            calendarDays.add(new LoopCalendarResponse.CalendarDay(currentDate, hasLoop));
        });

        // 결과 반환
        return LoopCalendarResponse.builder()
                .days(calendarDays)
                .build();
    }

    // ========== 비즈니스 로직 메서드 ==========
    // 단일 루프 (반복x)
    private Loop createSingleLoop(LoopCreateRequest requestDTO, CurrentUserDto currentUser) {
        LocalDate date = (requestDTO.specificDate() == null)
                ? LocalDate.now()
                : requestDTO.specificDate();

        Loop loop = buildLoop(requestDTO, currentUser, date, null);
        return loopRepository.save(loop);
    }

    // 매주 반복 루프
    private List<Loop> createWeeklyLoops(
            LoopCreateRequest requestDTO,
            CurrentUserDto currentUser,
            LoopRule loopRule
    ) {
        List<Loop> loopsToCreate = new ArrayList<>();

        for (LocalDate currentDate = loopRule.getStartDate();
             !currentDate.isAfter(loopRule.getEndDate());
             currentDate = currentDate.plusDays(1)) {

            if (loopRule.getDaysOfWeek().contains(currentDate.getDayOfWeek())) {
                loopsToCreate.add(
                        buildLoop(requestDTO, currentUser, currentDate, loopRule)
                );
            }
        }

        if (!loopsToCreate.isEmpty()) {
            loopRepository.saveAll(loopsToCreate);
        }

        return loopsToCreate;
    }

    // 매월 반복 루프
    private List<Loop> createMonthlyLoops(
            LoopCreateRequest requestDTO,
            CurrentUserDto currentUser,
            LoopRule loopRule
    ) {
        List<Loop> loopsToCreate = new ArrayList<>();
        LocalDate currentDate = loopRule.getStartDate();
        int monthsToAdd = 0;

        // 시작일이 과거라면 오늘 이후로 이동
        while (currentDate.isBefore(LocalDate.now())) {
            currentDate = loopRule.getStartDate().plusMonths(++monthsToAdd);
        }

        while (!currentDate.isAfter(loopRule.getEndDate())) {
            loopsToCreate.add(
                    buildLoop(requestDTO, currentUser, currentDate, loopRule)
            );
            monthsToAdd++;
            currentDate = loopRule.getStartDate().plusMonths(monthsToAdd);
        }

        if (!loopsToCreate.isEmpty()) {
            loopRepository.saveAll(loopsToCreate);
        }

        return loopsToCreate;
    }

    // 매년 반복 루프
    private List<Loop> createYearlyLoops(
            LoopCreateRequest requestDTO,
            CurrentUserDto currentUser,
            LoopRule loopRule
    ) {
        List<Loop> loopsToCreate = new ArrayList<>();
        LocalDate currentDate = loopRule.getStartDate();
        int yearsToAdd = 0;

        while (currentDate.isBefore(LocalDate.now())) {
            currentDate = loopRule.getStartDate().plusYears(++yearsToAdd);
        }

        while (!currentDate.isAfter(loopRule.getEndDate())) {
            loopsToCreate.add(
                    buildLoop(requestDTO, currentUser, currentDate, loopRule)
            );
            yearsToAdd++;
            currentDate = loopRule.getStartDate().plusYears(yearsToAdd);
        }

        if (!loopsToCreate.isEmpty()) {
            loopRepository.saveAll(loopsToCreate);
        }

        return loopsToCreate;
    }

    // loopRule(그룹) 생성
    private LoopRule createLoopRule(LoopCreateRequest requestDTO, CurrentUserDto currentUser) {
        LocalDate start = (requestDTO.startDate() == null) ? LocalDate.now() : requestDTO.startDate();
        LocalDate end = (requestDTO.endDate() == null) ? start.plusYears(5) : requestDTO.endDate();

        LoopRule loopRule = LoopRule.builder()
                .member(memberConverter.toMember(currentUser))
                .scheduleType(requestDTO.scheduleType())
                .daysOfWeek(requestDTO.scheduleType() == RepeatType.WEEKLY
                        ? toDayOfWeekSet(requestDTO.daysOfWeek())
                        : null) // WEEKLY가 아니면 null이 저장됨
                .startDate(start)
                .endDate(end)
                .build();

        loopRuleRepository.save(loopRule);
        return loopRule;
    }

    // 루프 생성
    private Loop buildLoop(LoopCreateRequest requestDTO, CurrentUserDto currentUser, LocalDate date,
                           LoopRule loopRule) {
        Loop loop = Loop.builder()
                .member(memberConverter.toMember(currentUser))
                .title(requestDTO.title())
                .content(requestDTO.content())
                .loopDate(date)
                .loopRule(loopRule)
                .build();

        // 입력한 체크리스트가 있다면 해당 루프에 추가
        if (requestDTO.checklists() != null && !requestDTO.checklists().isEmpty()) {
            for (String checklistContent : requestDTO.checklists()) {
                loop.addChecklist(LoopChecklist.builder().content(checklistContent).build());
            }
        }
        return loop;
    }

    // 전체 수정 시, 추가 루프 생성
    @Transactional
    public List<Loop> createUpdateLoop(
            LoopCreateRequest requestDTO,
            LoopRule loopRule,
            CurrentUserDto currentUser
    ) {
        List<Loop> createdLoops = new ArrayList<>();

        if (requestDTO.scheduleType() == RepeatType.NONE) {
            Loop loop = createSingleLoop(requestDTO, currentUser);
            createdLoops.add(loop);
            return createdLoops;
        }

        loopRule.setScheduleType(requestDTO.scheduleType());
        loopRule.setDaysOfWeek(
                requestDTO.scheduleType() == RepeatType.WEEKLY
                        ? toDayOfWeekSet(requestDTO.daysOfWeek())
                        : null
        );
        loopRule.setStartDate(requestDTO.startDate());
        loopRule.setEndDate(requestDTO.endDate());

        switch (requestDTO.scheduleType()) {
            case WEEKLY:
                createdLoops.addAll(createWeeklyLoops(requestDTO, currentUser, loopRule));
                break;
            case MONTHLY:
                createdLoops.addAll(createMonthlyLoops(requestDTO, currentUser, loopRule));
                break;
            case YEARLY:
                createdLoops.addAll(createYearlyLoops(requestDTO, currentUser, loopRule));
                break;
        }

        return createdLoops;
    }

    // ========== 조회 메서드 ==========
    // 그룹의 루프 전체를 리스트로 조회 (오늘 포함 미래만 조회)
    private List<Loop> findAllByLoopRule(LoopRule loopRule, LocalDate today) {
        List<Loop> loopList = loopRepository.findAllByLoopRuleAndLoopDateAfter(loopRule, today);
        return loopList;
    }

    private List<Loop> findAllByLoopRulePast(LoopRule loopRule, LocalDate today) {
        List<Loop> loopList = loopRepository.findAllByLoopRuleAndLoopDateBefore(loopRule, today);
        return loopList;
    }

    // ========== 헬퍼 메서드 ==========
    // 요청 페이지 수 제한
    private void checkPageSize(int pageSize) {
        int maxPageSize = LoopPage.getMaxPageSize();
        if (pageSize > maxPageSize) {
            throw new ServiceException(ReturnCode.PAGE_REQUEST_FAIL);
        }
    }

    // List -> Set 변환
    private Set<DayOfWeek> toDayOfWeekSet(List<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return null; // WEEKLY 아니면 null 저장하려는 의도 유지
        return EnumSet.copyOf(days); // 중복 제거 + Enum 최적화 Set
    }

    // ========== 검증 메서드 ==========
    // 루프 사용자 검증
    public void validateLoopOwner(Loop loop, CurrentUserDto currentUser) {
        if (!loop.getMember().getId().equals(currentUser.id())) {
            throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        }
    }

    // LoopRule 사용자 검증
    public void validateLoopRuleOwner(LoopRule loopRule, CurrentUserDto currentUser) {
        if (!loopRule.getMember().getId().equals(currentUser.id())) {
            throw new ServiceException(ReturnCode.NOT_AUTHORIZED);
        }
    }

    private ChatRoom linkLoopToChatRoom(Long chatRoomId, Loop loop) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new ServiceException(ReturnCode.CHATROOM_NOT_FOUND));
        chatRoom.selectLoop(loop);
        return chatRoom;
    }

    // ========== 캐시 무효화 메서드 ==========
    // 트랜잭션 커밋 이후 관련 캐시 무효화 (롤백 시 유지)
    private void evictLoopCachesAfterCommit(
            Long memberId,
            Collection<Long> loopIds,
            Collection<LocalDate> dates,
            Collection<YearMonth> yearMonths
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictLoopCaches(memberId, loopIds, dates, yearMonths);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictLoopCaches(memberId, loopIds, dates, yearMonths);
            }
        });
    }

    // 실제 캐시 무효화 로직
    private void evictLoopCaches(
            Long memberId,
            Collection<Long> loopIds,
            Collection<LocalDate> dates,
            Collection<YearMonth> yearMonths
    ) {
        // 1) loopReport
        Cache reportCache = cacheManager.getCache("loopReport");
        if (reportCache != null) {
            reportCache.evictIfPresent(memberId);
            log.debug("LoopReport cache evicted: {}", memberId);
        }
        // 2) loopDetail
        Cache detailCache = cacheManager.getCache("loopDetail");
        if (detailCache != null && loopIds != null) {
            for (Long loopId : loopIds) {
                if (loopId == null) continue;
                detailCache.evictIfPresent(memberId + ":" + loopId);
            }
        }
        // 3) dailyLoops
        Cache dailyCache = cacheManager.getCache("dailyLoops");
        if (dailyCache != null && dates != null) {
            for (LocalDate d : dates) {
                if (d == null) continue;
                dailyCache.evictIfPresent(memberId + ":" + d);
            }
        }
        // 4) loopCalendar
        Cache calendarCache = cacheManager.getCache("loopCalendar");
        if (calendarCache != null && yearMonths != null) {
            for (YearMonth ym : yearMonths) {
                if (ym == null) continue;
                calendarCache.evictIfPresent(memberId + ":" + ym.getYear() + ":" + ym.getMonthValue());
            }
        }
    }
}
