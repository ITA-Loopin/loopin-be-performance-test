package com.loopone.loopinbe.domain.loop.ai.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopone.loopinbe.domain.loop.ai.dto.AiPayload;
import com.loopone.loopinbe.domain.loop.ai.dto.res.RecommendationsLoop;
import com.loopone.loopinbe.domain.loop.ai.service.AiProvider;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopDetailResponse;
import com.loopone.loopinbe.global.exception.ReturnCode;
import com.loopone.loopinbe.global.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Collections;
import java.util.stream.Collectors;

import static com.loopone.loopinbe.global.constants.Constant.CREATE_LOOP_PROMPT;
import static com.loopone.loopinbe.global.constants.Constant.UPDATE_LOOP_PROMPT;

@Slf4j
public abstract class AbstractAiProvider implements AiProvider {
    protected final ObjectMapper objectMapper;

    protected AbstractAiProvider(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    @Override
    public abstract RecommendationsLoop callOpenAi(AiPayload payload);

    protected RecommendationsLoop execute(AiPayload payload) {
        String prompt = buildPrompt(payload);
        String raw = callModel(prompt);
        RecommendationsLoop parsed = parse(raw);

        Long loopRuleId = null;
        if (payload.loopDetailResponse() != null && payload.loopDetailResponse().loopRule() != null) {
            loopRuleId = payload.loopDetailResponse().loopRule().ruleId();
        }

        return new RecommendationsLoop(
                parsed.title(),
                loopRuleId,
                parsed.recommendations()
        );
    }

    protected String buildPrompt(AiPayload payload) {
        if (payload.loopDetailResponse() != null) {
            return updatePrompt(payload.userContent(), payload.loopDetailResponse());
        }
        return createPrompt(payload.userContent());
    }

    protected RecommendationsLoop parse(String result) {
        try {
            objectMapper.readTree(result);
            return objectMapper.readValue(result, RecommendationsLoop.class);
        } catch (JsonProcessingException e) {
            log.warn("AI 응답 JSON 파싱 실패", e);
            return new RecommendationsLoop(null, null, Collections.emptyList());
        }
    }

    private String createPrompt(String message) {
        String today = LocalDate.now().toString();
        return CREATE_LOOP_PROMPT.formatted(today) + message;
    }

    private String updatePrompt(String message, LoopDetailResponse loop) {
        String checklistText = loop.checklists().stream()
                .map(c -> "- " + c.content())
                .collect(Collectors.joining("\n"));

        return UPDATE_LOOP_PROMPT.formatted(
                loop.id(),
                loop.title(),
                loop.content(),
                loop.loopDate(),
                loop.progress(),
                checklistText,
                formatLoopRule(loop.loopRule()),
                message
        );
    }

    private String formatLoopRule(LoopDetailResponse.LoopRuleDTO rule) {
        if (rule == null) {
            return "반복 규칙 없음";
        }

        return String.format("""
                        - Rule ID: %d
                        - Schedule Type: %s
                        - Days of Week: %s
                        - Start: %s
                        - End: %s
                        """,
                rule.ruleId(),
                rule.scheduleType(),
                rule.daysOfWeek(),
                rule.startDate(),
                rule.endDate());
    }

    protected abstract String callModel(String prompt);

    protected RecommendationsLoop fallback(Throwable t) {
        log.warn("{} fallback triggered", getName(), t);
        throw new ServiceException(ReturnCode.OPEN_AI_INTERNAL_ERROR);
    }
}