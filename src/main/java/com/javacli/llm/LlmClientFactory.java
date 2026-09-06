package com.javacli.llm;

import com.javacli.config.JavaCliConfig;

public class LlmClientFactory {

    private LlmClientFactory() {}

    public static LlmClient create(String provider, JavaCliConfig config) {
        if (provider == null) return null;

        String normalized = normalizeProvider(provider);
        String configuredProvider = provider.trim().toLowerCase();
        String apiKey = config.getApiKey(normalized);
        if ((apiKey == null || apiKey.isBlank()) && !configuredProvider.equals(normalized)) {
            apiKey = config.getApiKey(configuredProvider);
        }
        if ("ollama".equals(normalized) && (apiKey == null || apiKey.isBlank())) {
            apiKey = "ollama";
        }
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        String model = firstConfigured(config.getModel(normalized),
                configuredProvider.equals(normalized) ? null : config.getModel(configuredProvider));
        String baseUrl = firstConfigured(config.getBaseUrl(normalized),
                configuredProvider.equals(normalized) ? null : config.getBaseUrl(configuredProvider));

        return switch (normalized) {
            case "glm" -> new GLMClient(apiKey, model);
            case "deepseek" -> new DeepSeekClient(apiKey, model);
            case "step" -> new StepClient(apiKey, model, baseUrl);
            case "kimi" -> new KimiClient(apiKey, model, baseUrl);
            case "openai" -> new GenericOpenAiClient("openai", apiKey, model != null ? model : "gpt-4o",
                    baseUrl != null ? baseUrl : "https://api.openai.com/v1");
            case "ollama" -> new GenericOpenAiClient("ollama", apiKey, model != null ? model : "llama3",
                    baseUrl != null ? baseUrl : "http://localhost:11434/v1");
            case "custom" -> new GenericOpenAiClient("custom", apiKey, model, baseUrl);
            default -> {
                if (baseUrl != null && !baseUrl.isBlank()) {
                    String effectiveModel = (model != null && !model.isBlank()) ? model : "default";
                    yield new GenericOpenAiClient(configuredProvider, apiKey, effectiveModel, baseUrl);
                }
                yield null;
            }
        };
    }

    public static LlmClient createFromConfig(JavaCliConfig config) {
        if (config == null) return null;
        LlmClient client = create(config.getDefaultProvider(), config);
        if (client != null) {
            return client;
        }

        if (config.getProviders() != null) {
            for (String provider : config.getProviders().keySet()) {
                client = create(provider, config);
                if (client != null) {
                    return client;
                }
            }
        }

        for (String provider : new String[]{"deepseek", "glm", "step", "kimi", "openai", "ollama", "custom"}) {
            client = create(provider, config);
            if (client != null) {
                return client;
            }
        }

        return null;
    }

    private static String normalizeProvider(String provider) {
        String normalized = provider.trim().toLowerCase();
        return switch (normalized) {
            case "stepfun", "step-fun" -> "step";
            case "moonshot", "moonshotai", "moonshot-ai" -> "kimi";
            default -> normalized;
        };
    }

    private static String firstConfigured(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
