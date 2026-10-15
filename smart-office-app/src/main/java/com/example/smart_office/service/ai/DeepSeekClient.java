package com.example.smart_office.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek LLM 客户端
 *
 * <p>用原生 HTTP 调 DeepSeek API，兼容 OpenAI Chat Completions 格式。
 * <p>通过配置切换：ai.llm.provider=deepseek
 */
@Component
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "deepseek")
public class DeepSeekClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    @Value("${ai.llm.deepseek.api-key}")
    private String apiKey;

    @Value("${ai.llm.deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${ai.llm.deepseek.model:deepseek-chat}")
    private String model;

    private final RestClient restClient = RestClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            // 1. 构造请求体（OpenAI Chat Completions 格式）
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.3,
                    "stream", false
            );

            // 2. 发 HTTP 请求
            String url = baseUrl + "/v1/chat/completions";
            String response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            // 3. 解析响应
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            log.info("[DeepSeekClient] 响应: {}", content);
            return content;

        } catch (Exception e) {
            log.error("[DeepSeekClient] 调用失败: {}", e.getMessage(), e);
            // 降级：返回错误提示，不抛异常（避免影响主链路）
            return "[DEEPSEEK-ERROR] LLM 调用失败: " + e.getMessage();
        }
    }
}