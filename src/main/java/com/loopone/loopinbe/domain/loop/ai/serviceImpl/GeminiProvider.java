package com.loopone.loopinbe.domain.loop.ai.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopone.loopinbe.domain.loop.ai.dto.AiPayload;
import com.loopone.loopinbe.domain.loop.ai.dto.res.RecommendationsLoop;
import com.loopone.loopinbe.domain.loop.ai.dto.type.AiType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Order(2)
public class GeminiProvider extends AbstractAiProvider {
    private final ChatClient chatClient;

    protected GeminiProvider(
            ObjectMapper objectMapper,
            @Qualifier("geminiChatModel") ChatModel geminiChatModel) {
        super(objectMapper);
        this.chatClient = ChatClient.builder(geminiChatModel).build();
    }

    @Override
    @CircuitBreaker(name = "gemini", fallbackMethod = "fallback")
    @Retryable(backoff = @Backoff(delay = 1500, multiplier = 2))
    public RecommendationsLoop callOpenAi(AiPayload payload) {
        return execute(payload);
    }

    @Override
    protected String callModel(String prompt) {
        log.info("Gemini Call");
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    @Override
    public String getName() {
        return AiType.GEMINI.name();
    }
}
