package com.javacli.cli;

import com.javacli.config.JavaCliConfig;
import com.javacli.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenCodeModelPickerTest {

    private static class StubLlmClient implements LlmClient {
        private final String provider;
        private final String model;

        StubLlmClient(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        @Override
        public String getProviderName() {
            return provider;
        }

        @Override
        public String getModelName() {
            return model;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools) {
            return null;
        }

        @Override
        public ChatResponse chat(List<Message> messages, List<Tool> tools, StreamListener listener) {
            return null;
        }
    }

    @Test
    void testBuildEntriesWithActiveClientAndCustomConfig() {
        JavaCliConfig config = new JavaCliConfig();
        config.setDefaultProvider("sensenova");
        config.setApiKey("sensenova", "sk-test12345");
        config.setBaseUrl("sensenova", "https://token.sensenova.cn/v1");
        config.setModel("sensenova", "deepseek-v4-flash");

        config.setBaseUrl("ollama", "http://localhost:11434/v1");
        config.setModel("ollama", "llama3");

        StubLlmClient activeClient = new StubLlmClient("sensenova", "deepseek-v4-flash");

        List<OpenCodeModelPicker.ModelEntry> entries = OpenCodeModelPicker.buildEntries(config, activeClient);
        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        // 活跃模型应当在首位，且标为 [active]
        OpenCodeModelPicker.ModelEntry first = entries.get(0);
        assertEquals("sensenova/deepseek-v4-flash", first.identifier());
        assertEquals("[active]", first.badge());

        // 本地 ollama 应该标记为 [local]
        boolean hasLocalOllama = entries.stream()
                .anyMatch(e -> "ollama/llama3".equals(e.identifier()) && "[local]".equals(e.badge()));
        assertTrue(hasLocalOllama, "Should have local Ollama entry with [local] badge");

        // 列表中最后一个应当是操作项 ➕ Connect new provider...
        OpenCodeModelPicker.ModelEntry last = entries.get(entries.size() - 1);
        assertTrue(last.isAction());
        assertEquals(OpenCodeModelPicker.ACTION_CONNECT_NEW, last.displayName());
    }

    @Test
    void testFilterEntriesWithTypeToSearch() {
        JavaCliConfig config = new JavaCliConfig();
        StubLlmClient activeClient = new StubLlmClient("deepseek", "deepseek-chat");
        List<OpenCodeModelPicker.ModelEntry> all = OpenCodeModelPicker.buildEntries(config, activeClient);

        // 1. 空查询：全部返回
        List<OpenCodeModelPicker.ModelEntry> emptyQuery = OpenCodeModelPicker.filterEntries(all, "");
        assertEquals(all.size(), emptyQuery.size());

        // 2. 搜索 "deep"：包含 deepseek 系列以及 action 项
        List<OpenCodeModelPicker.ModelEntry> deepQuery = OpenCodeModelPicker.filterEntries(all, "deep");
        assertTrue(deepQuery.size() >= 2);
        assertTrue(deepQuery.stream().anyMatch(e -> e.identifier().contains("deepseek")));
        assertTrue(deepQuery.stream().anyMatch(OpenCodeModelPicker.ModelEntry::isAction));

        // 3. 搜索 "glm"：包含 glm 系列
        List<OpenCodeModelPicker.ModelEntry> glmQuery = OpenCodeModelPicker.filterEntries(all, "glm");
        assertTrue(glmQuery.stream().anyMatch(e -> e.identifier().contains("glm")));

        // 4. 搜索不存在的关键词：至少保留 action 项便于新建
        List<OpenCodeModelPicker.ModelEntry> noneQuery = OpenCodeModelPicker.filterEntries(all, "nonexistent-xyz");
        assertEquals(1, noneQuery.size());
        assertTrue(noneQuery.get(0).isAction());
    }

    @Test
    void testRenderDialogLines() {
        JavaCliConfig config = new JavaCliConfig();
        StubLlmClient activeClient = new StubLlmClient("deepseek", "deepseek-chat");
        List<OpenCodeModelPicker.ModelEntry> all = OpenCodeModelPicker.buildEntries(config, activeClient);
        List<OpenCodeModelPicker.ModelEntry> filtered = OpenCodeModelPicker.filterEntries(all, "deep");

        List<String> lines = OpenCodeModelPicker.renderDialogLines(filtered, 0, "deep", 80);
        assertNotNull(lines);
        assertFalse(lines.isEmpty());

        // 校验顶边框
        assertTrue(lines.get(0).contains("Select Model"));
        // 校验搜索框
        assertTrue(lines.get(1).contains("Search:"));
        assertTrue(lines.get(1).contains("deep"));
        // 校验底边框包含快捷键说明
        String lastLine = lines.get(lines.size() - 1);
        assertTrue(lastLine.contains("navigate"));
        assertTrue(lastLine.contains("type to search"));
        assertTrue(lastLine.contains("enter select"));
        assertTrue(lastLine.contains("esc cancel"));
    }

    @Test
    void testProviderWithBaseUrlButNoApiKeyIsBadgedAsNeedsKey() {
        JavaCliConfig config = new JavaCliConfig();
        // Sensenova configured with custom Base URL but null API Key
        config.setBaseUrl("sensenova", "https://token.sensenova.cn/v1");
        config.setModel("sensenova", "deepseek-v4-flash");

        List<OpenCodeModelPicker.ModelEntry> entries = OpenCodeModelPicker.buildEntries(config, null);
        OpenCodeModelPicker.ModelEntry sensenovaEntry = entries.stream()
                .filter(e -> "sensenova/deepseek-v4-flash".equals(e.identifier()))
                .findFirst()
                .orElse(null);

        assertNotNull(sensenovaEntry);
        assertEquals("[needs key]", sensenovaEntry.badge(), "Provider without API Key must be badged as [needs key]");
    }
}
