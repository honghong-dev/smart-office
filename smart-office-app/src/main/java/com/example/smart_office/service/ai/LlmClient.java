package com.example.smart_office.service.ai;

/**
 * LLM 客户端接口
 *
 * <p>抽象 LLM 调用，实现可替换：Mock / DeepSeek / OpenAI 等。
 */
public interface LlmClient {

    /**
     * 发送对话请求，返回 LLM 的响应文本。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM 响应文本
     */
    String chat(String systemPrompt, String userPrompt);
}