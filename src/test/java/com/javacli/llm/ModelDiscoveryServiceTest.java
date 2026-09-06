package com.javacli.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelDiscoveryServiceTest {

    @Test
    void parsesOpenAiStandardModelsJson() {
        String json = """
                {
                  "object": "list",
                  "data": [
                    { "id": "deepseek-chat", "object": "model" },
                    { "id": "deepseek-reasoner", "object": "model" }
                  ]
                }
                """;

        List<String> models = ModelDiscoveryService.parseModelsFromJson(json);

        assertEquals(2, models.size());
        assertEquals("deepseek-chat", models.get(0));
        assertEquals("deepseek-reasoner", models.get(1));
    }

    @Test
    void parsesOllamaTagsJson() {
        String json = """
                {
                  "models": [
                    { "name": "llama3:latest", "modified_at": "2024-05-01" },
                    { "name": "qwen2.5-coder:32b", "modified_at": "2024-05-02" }
                  ]
                }
                """;

        List<String> models = ModelDiscoveryService.parseModelsFromJson(json);

        assertEquals(2, models.size());
        assertTrue(models.contains("llama3:latest"));
        assertTrue(models.contains("qwen2.5-coder:32b"));
    }

    @Test
    void resolvesCandidateUrlsProperly() {
        List<String> urls = ModelDiscoveryService.resolveCandidateUrls("https://api.deepseek.com/v1");
        assertTrue(urls.contains("https://api.deepseek.com/v1/models"));

        List<String> directUrls = ModelDiscoveryService.resolveCandidateUrls("https://api.deepseek.com");
        assertTrue(directUrls.contains("https://api.deepseek.com/v1/models"));
        assertTrue(directUrls.contains("https://api.deepseek.com/models"));
    }

    @Test
    void normalizesBaseUrl() {
        assertEquals("https://api.openai.com/v1", ModelDiscoveryService.normalizeBaseUrl(null));
        assertEquals("https://api.openai.com/v1", ModelDiscoveryService.normalizeBaseUrl("   "));
        assertEquals("https://api.deepseek.com", ModelDiscoveryService.normalizeBaseUrl("https://api.deepseek.com///"));
    }
}
