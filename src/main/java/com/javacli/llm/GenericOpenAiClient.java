package com.javacli.llm;

/**
 * 通用 OpenAI 协议兼容客户端。
 *
 * <p>用于接入 OpenAI 官方、本地 Ollama、SiliconFlow、vLLM、OneAPI 以及任意自建的
 * {@code /v1/chat/completions} 端点，免去为每个第三方平台单独编写适配类的成本。
 */
public class GenericOpenAiClient extends AbstractOpenAiCompatibleClient {

    private final String providerName;
    private final String apiKey;
    private final String model;
    private final String apiUrl;
    private final int contextWindow;

    public GenericOpenAiClient(String providerName, String apiKey, String model, String baseUrl) {
        this(providerName, apiKey, model, baseUrl, 128_000);
    }

    public GenericOpenAiClient(String providerName, String apiKey, String model, String baseUrl, int contextWindow) {
        this.providerName = providerName != null && !providerName.isBlank() ? providerName.trim() : "openai";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = model != null && !model.isBlank() ? model.trim() : "gpt-4o";
        this.apiUrl = toChatCompletionsUrl(baseUrl);
        this.contextWindow = contextWindow > 0 ? contextWindow : 128_000;
    }

    @Override
    protected String getApiUrl() {
        return apiUrl;
    }

    @Override
    protected String getModel() {
        return model;
    }

    @Override
    protected String getApiKey() {
        return apiKey;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    protected boolean shouldSendReasoningContentInRequestHistory() {
        return true;
    }

    @Override
    public int maxContextWindow() {
        return contextWindow;
    }

    @Override
    public boolean supportsPromptCaching() {
        return true;
    }

    private static String toChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.openai.com/v1";
        String withoutTrailingSlash = normalized.replaceAll("/+$", "");
        if (withoutTrailingSlash.endsWith("/chat/completions")) {
            return withoutTrailingSlash;
        }
        return withoutTrailingSlash + "/chat/completions";
    }
}
