package com.javacli.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericOpenAiClientTest {

    @Test
    void initializesWithOpenAiDefaults() {
        GenericOpenAiClient client = new GenericOpenAiClient("openai", "sk-test", "gpt-4o", "https://api.openai.com/v1");

        assertEquals("openai", client.getProviderName());
        assertEquals("gpt-4o", client.getModelName());
        assertEquals("https://api.openai.com/v1/chat/completions", client.getApiUrl());
        assertEquals("sk-test", client.getApiKey());
        assertEquals(128_000, client.maxContextWindow());
        assertTrue(client.supportsPromptCaching());
    }

    @Test
    void handlesOllamaUrlAndCustomEndpoints() {
        GenericOpenAiClient client = new GenericOpenAiClient("ollama", "ollama", "qwen2.5-coder", "http://localhost:11434/v1");

        assertEquals("ollama", client.getProviderName());
        assertEquals("qwen2.5-coder", client.getModelName());
        assertEquals("http://localhost:11434/v1/chat/completions", client.getApiUrl());
    }

    @Test
    void normalizesExistingChatCompletionsUrl() {
        GenericOpenAiClient client = new GenericOpenAiClient("custom", "key", "model-x", "https://custom.api/v1/chat/completions/");

        assertEquals("https://custom.api/v1/chat/completions", client.getApiUrl());
    }
}
