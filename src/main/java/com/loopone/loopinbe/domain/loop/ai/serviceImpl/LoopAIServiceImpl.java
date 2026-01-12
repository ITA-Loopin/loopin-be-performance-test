package com.loopone.loopinbe.domain.loop.ai.serviceImpl;

import com.loopone.loopinbe.domain.loop.ai.dto.AiPayload;
import com.loopone.loopinbe.domain.loop.ai.dto.res.RecommendationsLoop;
import com.loopone.loopinbe.domain.loop.ai.service.LoopAIService;
import com.loopone.loopinbe.domain.loop.loop.dto.res.LoopDetailResponse;
import com.loopone.loopinbe.domain.loop.loop.entity.Loop;
import com.loopone.loopinbe.domain.loop.loop.mapper.LoopMapper;
import com.loopone.loopinbe.domain.loop.loop.repository.LoopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class LoopAIServiceImpl implements LoopAIService {
    private final AiRoute aiRoute;
    private final LoopRepository loopRepository;
    private final LoopMapper loopMapper;
    @Qualifier("openAiExecutor")
    private final Executor executor;

    public LoopAIServiceImpl(
            AiRoute aiRoute,
            LoopRepository loopRepository,
            LoopMapper loopMapper,
            @Qualifier("openAiExecutor") Executor executor
    ) {
        this.aiRoute = aiRoute;
        this.loopRepository = loopRepository;
        this.loopMapper = loopMapper;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<RecommendationsLoop> chat(AiPayload request) {
        log.info("OpenAI 요청 처리 시작: requestId={}", request.clientMessageId());
        
        AiPayload finalRequest = request;

        if (request.loopDetailResponse() != null && request.loopDetailResponse().loopRule() != null) {
            Long ruleId = request.loopDetailResponse().loopRule().ruleId();
            Loop relevantLoop = loopRepository.findFirstByLoopRuleIdAndLoopDateGreaterThanEqualOrderByLoopDateAsc(ruleId, LocalDate.now())
                    .orElse(null);

            if (relevantLoop != null) {
                LoopDetailResponse updatedResponse = loopMapper.toDetailResponse(relevantLoop);
                finalRequest = new AiPayload(
                        request.clientMessageId(),
                        request.chatRoomId(),
                        request.userMessageId(),
                        request.userId(),
                        request.userContent(),
                        updatedResponse,
                        request.requestedAt()
                );
            }
        }
        
        AiPayload payloadToSend = finalRequest;
        return CompletableFuture.supplyAsync(() -> aiRoute.route(payloadToSend), executor);
    }
}
