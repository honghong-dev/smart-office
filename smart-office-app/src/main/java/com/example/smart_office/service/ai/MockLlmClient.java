package com.example.smart_office.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock LLM 客户端
 *
 * <p>开发调试用：不调用真实 LLM，返回固定格式的假响应。
 * <p>通过配置切换：ai.llm.provider=mock（默认）→ mock，ai.llm.provider=deepseek → 真实
 */
@Component
@ConditionalOnProperty(name = "ai.llm.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        log.info("===== [MockLlmClient] 收到 LLM 请求 =====");
        log.info("System: {}", systemPrompt);
        log.info("User: {}", userPrompt);

        // 模拟 LLM 返回：识别出异常 + 建议发送告警
        String mockResponse = "[MOCK-LLM] 检测到设备异常。"
                + "根据上下文研判，该异常属于 CRITICAL 级别，"
                + "建议立即触发告警并记录工单。"
                + "分析完成。";

        log.info("Mock 响应: {}", mockResponse);
        return mockResponse;
    }
}