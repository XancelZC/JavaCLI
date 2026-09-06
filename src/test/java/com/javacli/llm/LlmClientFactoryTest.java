package com.javacli.llm;

import com.javacli.config.JavaCliConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmClientFactoryTest {

    @Test
    void createsGlm5vTurboClientWithMultimodalEndpoint() {
        JavaCliConfig config = new JavaCliConfig();
        config.getProviders().put("glm",
                new JavaCliConfig.ProviderConfig("test-glm-key", null, "glm-5v-turbo"));

        LlmClient client = LlmClientFactory.create("glm", config);

        GLMClient glmClient = assertInstanceOf(GLMClient.class, client);
        assertEquals("glm", glmClient.getProviderName());
        assertEquals("glm-5v-turbo", glmClient.getModelName());
        assertEquals("https://open.bigmodel.cn/api/paas/v4/chat/completions", glmClient.getApiUrl());
    }

    @Test
    void createsStepClientFromConfiguredProvider() {
        JavaCliConfig config = new JavaCliConfig();
        config.getProviders().put("step",
                new JavaCliConfig.ProviderConfig("test-step-key", null, "step-3.5-flash-2603"));

        LlmClient client = LlmClientFactory.create("step", config);

        StepClient stepClient = assertInstanceOf(StepClient.class, client);
        assertEquals("step", stepClient.getProviderName());
        assertEquals("step-3.5-flash-2603", stepClient.getModelName());
        assertEquals(256_000, stepClient.maxContextWindow());
        assertEquals(expectedStepChatUrl(config.getBaseUrl("step")), stepClient.getApiUrl());
    }

    @Test
    void createsStepClientFromStepfunAliasAndCustomBaseUrl() {
        JavaCliConfig config = new JavaCliConfig();
        config.setProviders(new LinkedHashMap<>());
        config.getProviders().put("step",
                new JavaCliConfig.ProviderConfig(
                        "test-step-key",
                        "https://api.stepfun.com/step_plan/v1",
                        "step-router-v1"));

        LlmClient client = LlmClientFactory.create("stepfun", config);

        StepClient stepClient = assertInstanceOf(StepClient.class, client);
        assertEquals("step-router-v1", stepClient.getModelName());
        assertEquals("https://api.stepfun.com/step_plan/v1/chat/completions", stepClient.getApiUrl());
    }

    @Test
    void createsKimiClientFromMoonshotAliasAndCustomBaseUrl() {
        JavaCliConfig config = new JavaCliConfig();
        config.setProviders(new LinkedHashMap<>());
        config.getProviders().put("kimi",
                new JavaCliConfig.ProviderConfig(
                        "test-kimi-key",
                        "https://api.moonshot.ai/v1",
                        "kimi-k2.6"));

        LlmClient client = LlmClientFactory.create("moonshot", config);

        KimiClient kimiClient = assertInstanceOf(KimiClient.class, client);
        assertEquals("kimi", kimiClient.getProviderName());
        assertEquals("kimi-k2.6", kimiClient.getModelName());
        assertEquals(256_000, kimiClient.maxContextWindow());
    }

    @Test
    void createsOpenAiClientFromConfig() {
        JavaCliConfig config = new JavaCliConfig();
        config.getProviders().put("openai", new JavaCliConfig.ProviderConfig("sk-test", "https://api.openai.com/v1", "gpt-4o"));

        LlmClient client = LlmClientFactory.create("openai", config);
        GenericOpenAiClient openAiClient = assertInstanceOf(GenericOpenAiClient.class, client);
        assertEquals("openai", openAiClient.getProviderName());
        assertEquals("gpt-4o", openAiClient.getModelName());
    }

    @Test
    void createsOllamaClientEvenWithoutApiKey() {
        JavaCliConfig config = new JavaCliConfig();
        config.getProviders().put("ollama", new JavaCliConfig.ProviderConfig(null, "http://localhost:11434/v1", "llama3"));

        LlmClient client = LlmClientFactory.create("ollama", config);
        GenericOpenAiClient ollamaClient = assertInstanceOf(GenericOpenAiClient.class, client);
        assertEquals("ollama", ollamaClient.getProviderName());
        assertEquals("llama3", ollamaClient.getModelName());
    }

    @Test
    void createsCustomOpenAiCompatibleClient() {
        JavaCliConfig config = new JavaCliConfig();
        config.getProviders().put("custom", new JavaCliConfig.ProviderConfig("custom-key", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3"));

        LlmClient client = LlmClientFactory.create("custom", config);
        GenericOpenAiClient customClient = assertInstanceOf(GenericOpenAiClient.class, client);
        assertEquals("custom", customClient.getProviderName());
        assertEquals("deepseek-ai/DeepSeek-V3", customClient.getModelName());
        assertEquals("https://api.siliconflow.cn/v1/chat/completions", customClient.getApiUrl());
    }

    @Test
    void returnsNullForUnknownProviderWithoutBaseUrl() {
        JavaCliConfig config = new JavaCliConfig();
        config.getProviders().put("unknown", new JavaCliConfig.ProviderConfig("test-key", null, "unknown-model"));

        assertNull(LlmClientFactory.create("unknown", config));
    }

    @Test
    void createsGenericClientForThirdPartyProviderWithBaseUrl() {
        JavaCliConfig config = new JavaCliConfig();
        config.getProviders().put("sensenova",
                new JavaCliConfig.ProviderConfig("test-key", "https://token.sensenova.cn/v1", "deepseek-v4-flash"));

        LlmClient client = LlmClientFactory.create("sensenova", config);
        GenericOpenAiClient genericClient = assertInstanceOf(GenericOpenAiClient.class, client);
        assertEquals("sensenova", genericClient.getProviderName());
        assertEquals("deepseek-v4-flash", genericClient.getModelName());
        assertEquals("https://token.sensenova.cn/v1/chat/completions", genericClient.getApiUrl());
    }

    private static String expectedStepChatUrl(String baseUrl) {
        String normalized = baseUrl != null && !baseUrl.isBlank()
                ? baseUrl.trim()
                : "https://api.stepfun.com/v1";
        String withoutTrailingSlash = normalized.replaceAll("/+$", "");
        if (withoutTrailingSlash.endsWith("/chat/completions")) {
            return withoutTrailingSlash;
        }
        return withoutTrailingSlash + "/chat/completions";
    }
}
