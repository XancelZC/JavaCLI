package com.javacli.llm;

import com.javacli.config.JavaCliConfig;

public class LlmClientFactory {

    private LlmClientFactory() {}

    public static LlmClient create(String provider, JavaCliConfig config) {
        if (provider == null) return null;

        String normalized = provider.toLowerCase();
        String apiKey = config.getApiKey(normalized);
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        String model = config.getModel(normalized);

        return switch (normalized) {
            case "glm" -> new GLMClient(apiKey, model);
            case "deepseek" -> new DeepSeekClient(apiKey, model);
            default -> null;
        };
    }

    public static LlmClient createFromConfig(JavaCliConfig config) {
        LlmClient client = create(config.getDefaultProvider(), config);
        if (client != null) {
            return client;
        }

        for (String provider : new String[]{"glm", "deepseek"}) {
            client = create(provider, config);
            if (client != null) {
                return client;
            }
        }

        return null;
    }
}
