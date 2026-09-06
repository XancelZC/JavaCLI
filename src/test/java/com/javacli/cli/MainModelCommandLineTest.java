package com.javacli.cli;

import com.javacli.config.JavaCliConfig;
import com.javacli.llm.LlmClient;
import com.javacli.render.Renderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainModelCommandLineTest {

    @Test
    void maskApiKeyMasksLongAndShortKeys() {
        assertEquals("(未配置)", Main.maskApiKey(null));
        assertEquals("(未配置)", Main.maskApiKey("  "));
        assertEquals("********", Main.maskApiKey("12345678"));
        assertEquals("sk-****cdef", Main.maskApiKey("sk-1234567890abcdef"));
    }

    @Test
    void handleModelKeyCommandUpdatesConfig() {
        JavaCliConfig config = new JavaCliConfig();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        Main.handleModelKeyCommand(ui, config, llmRef, null, renderer, null, null, null, "key deepseek sk-mysecretkey123");

        assertEquals("sk-mysecretkey123", config.getApiKey("deepseek"));
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("sk-****y123"), printed);
        assertFalse(printed.contains("sk-mysecretkey123"), "明文密钥不得直接回显在界面");
    }

    @Test
    void handleModelUrlCommandUpdatesConfigAndValidatesUrl() {
        JavaCliConfig config = new JavaCliConfig();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        // 校验非法 URL
        Main.handleModelUrlCommand(ui, config, llmRef, null, renderer, null, null, null, "url deepseek ftp://api.com");
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("必须以 http:// 或 https:// 开头"));

        // 正常更新合法 URL
        out.reset();
        Main.handleModelUrlCommand(ui, config, llmRef, null, renderer, null, null, null, "url deepseek https://api.deepseek.com/v1");
        assertEquals("https://api.deepseek.com/v1", config.getBaseUrl("deepseek"));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("已成功设置 deepseek 的 Base URL"));
    }

    @Test
    void handleModelConfigCommandParsesFlags() {
        JavaCliConfig config = new JavaCliConfig();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        Main.handleModelConfigCommand(ui, config, llmRef, null, renderer, null, null, null,
                "config custom --key my-key-1234 --url http://localhost:11434/v1 --model qwen2.5");

        assertEquals("my-key-1234", config.getApiKey("custom"));
        assertEquals("http://localhost:11434/v1", config.getBaseUrl("custom"));
        assertEquals("qwen2.5", config.getModel("custom"));
    }

    @Test
    void printModelShowDisplaysFormattedTable() {
        JavaCliConfig config = new JavaCliConfig();
        config.setApiKey("deepseek", "sk-abcdef123456");
        config.setBaseUrl("deepseek", "https://api.deepseek.com/v1");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);
        Main.printModelShow(ui, config, llmRef, "show");

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("deepseek"), output);
        assertTrue(output.contains("sk-****3456"), output);
        assertTrue(output.contains("https://api.deepseek.com/v1"), output);
        assertFalse(output.contains("sk-abcdef123456"), "不得展示完整明文密钥");
    }

    @Test
    void handleModelCommandWithBareKeyRoutesToKeyCommandNotProvider() {
        JavaCliConfig config = new JavaCliConfig();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        Mockito.when(renderer.stream()).thenReturn(ui);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        Main.handleModelCommand(null, null, renderer, config, llmRef, null, null, null, null, "key");

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("使用方法：/model key"), output);
        assertFalse(output.contains("未检测到 key 的有效配置"), "不应把 key 当做供应商名称进入向导");
    }

    @Test
    void handleModelCommandWithBareUrlRoutesToUrlCommandNotProvider() {
        JavaCliConfig config = new JavaCliConfig();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        Mockito.when(renderer.stream()).thenReturn(ui);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        Main.handleModelCommand(null, null, renderer, config, llmRef, null, null, null, null, "url");

        String output = out.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("使用方法：/model url"), output);
        assertFalse(output.contains("未检测到 url 的有效配置"), "不应把 url 当做供应商名称进入向导");
    }

    @Test
    void handleModelKeyCommandWithSingleArgBindsToCurrentProvider() {
        JavaCliConfig config = new JavaCliConfig();
        config.setDefaultProvider("deepseek");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        // 用户输入 /model key sk-abc1234567890xyz123456
        Main.handleModelKeyCommand(ui, config, llmRef, null, renderer, null, null, null, "key sk-abc1234567890xyz123456");

        assertEquals("sk-abc1234567890xyz123456", config.getApiKey("deepseek"));
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("已成功设置 deepseek 的 API Key"), printed);
    }

    @Test
    void handleModelUrlCommandWithSingleArgBindsToCurrentProvider() {
        JavaCliConfig config = new JavaCliConfig();
        config.setDefaultProvider("sensenova");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        // 用户输入 /model url https://token.sensenova.cn/v1
        Main.handleModelUrlCommand(null, null, ui, config, llmRef, null, renderer, null, null, null, "url https://token.sensenova.cn/v1");

        assertEquals("https://token.sensenova.cn/v1", config.getBaseUrl("sensenova"));
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("已成功设置 sensenova 的 Base URL"), printed);
    }

    @Test
    void switchModelDirectPromptsForMissingApiKeyInInteractiveMode() {
        JavaCliConfig config = new JavaCliConfig();
        config.setBaseUrl("mycustom", "https://api.mycustom.ai/v1");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        Mockito.when(renderer.stream()).thenReturn(ui);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        org.jline.terminal.Terminal terminal = Mockito.mock(org.jline.terminal.Terminal.class);
        org.jline.reader.LineReader lineReader = Mockito.mock(org.jline.reader.LineReader.class);
        Mockito.when(lineReader.readLine(Mockito.anyString(), Mockito.any(), Mockito.eq('*'), Mockito.any()))
                .thenReturn("sk-input-interactive-key");

        Main.switchModelDirect(terminal, lineReader, renderer, config, llmRef, null, null, null, null, "mycustom/custom-model");

        assertEquals("sk-input-interactive-key", config.getApiKey("mycustom"));
        assertNotNull(llmRef.get());
        assertEquals("custom-model", llmRef.get().getModelName());
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("已成功切换底层模型"), out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void switchModelDirectFailsGracefullyWhenNonInteractiveAndKeyMissing() {
        JavaCliConfig config = new JavaCliConfig();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream ui = new PrintStream(out);

        Renderer renderer = Mockito.mock(Renderer.class);
        Mockito.when(renderer.stream()).thenReturn(ui);
        AtomicReference<LlmClient> llmRef = new AtomicReference<>(null);

        Main.switchModelDirect(null, null, renderer, config, llmRef, null, null, null, null, "mockprov/test-model");

        assertNull(llmRef.get());
        String printed = out.toString(StandardCharsets.UTF_8);
        assertTrue(printed.contains("切换失败：未检测到 mockprov 可用的 API Key"), printed);
        assertTrue(printed.contains("[error]"), printed);
    }
}
