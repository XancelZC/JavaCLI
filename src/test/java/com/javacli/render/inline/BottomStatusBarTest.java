package com.javacli.render.inline;

import com.javacli.render.StatusInfo;
import com.javacli.util.AnsiStyle;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomStatusBarTest {

    @Test
    void formatStatusLineIncludesAllFields() {
        StatusInfo info = StatusInfo.tokens("glm-5.1", 200_000L, 1000L, 234L, 100L, "¥0.0123",
                true, 1500L, "running");
        String line = BottomStatusBar.formatFooterLine(info, 200);
        assertTrue(line.contains("Auto Model"), line);
        assertTrue(line.contains("running"), line);
        assertTrue(line.contains("glm-5.1"), line);
        assertTrue(line.contains("ctx"), line);
        assertTrue(line.contains("1%"), line);
        assertTrue(line.contains("1.2k/200.0k"), line);
        assertTrue(line.contains("in 1.0k out 234"), line);
        assertTrue(line.contains("cache 100"), line);
        assertTrue(line.contains("¥0.0123"), line);
        assertTrue(line.contains("1.5s"), line);
    }

    @Test
    void formatStatusLinePadsToColumnWidth() {
        StatusInfo info = new StatusInfo("glm-5.1", 0L, 200_000L, false, 0L);
        String line = BottomStatusBar.formatStatusLine(info, 80);
        assertEquals(80, AnsiStyle.displayWidth(line), "status line should fill the bar: " + AnsiStyle.displayWidth(line));
    }

    @Test
    void formatStatusLineTruncatesWhenLong() {
        StatusInfo info = new StatusInfo("very-long-model-name-exceeding-cols",
                999_999L, 200_000L, true, 0L);
        String line = BottomStatusBar.formatStatusLine(info, 30);
        assertEquals(30, AnsiStyle.displayWidth(line));
    }

    @Test
    void formatStatusLinePadsAndTruncatesWithCjkCharacters() {
        StatusInfo info = StatusInfo.active("glm-5.1", 200_000L, false, "智能体规划中")
                .withEnvironment("4 个服务", "2 个技能");

        String line80 = BottomStatusBar.formatStatusLine(info, 80);
        assertEquals(80, AnsiStyle.displayWidth(line80), "包含 CJK 字符的状态行应当对齐到指定终端列宽");

        String line30 = BottomStatusBar.formatStatusLine(info, 30);
        assertEquals(30, AnsiStyle.displayWidth(line30), "截断并填充后显示宽度必须精确等于列宽");
    }

    @Test
    void formatStatusLineHidesElapsedWhenZero() {
        StatusInfo info = new StatusInfo("glm-5.1", 0L, 200_000L, false, 0L);
        String line = BottomStatusBar.formatFooterLine(info, 80);
        assertFalse(line.contains("ms"));
        assertFalse(line.contains("0s"));
    }

    @Test
    void formatStatusLineHandlesMillisecondElapsed() {
        StatusInfo info = new StatusInfo("glm-5.1", 0L, 0L, false, 250L);
        String line = BottomStatusBar.formatFooterLine(info, 80);
        assertTrue(line.contains("250ms"), line);
    }

    @Test
    void footerLineFitsColumnWidth() {
        String line = BottomStatusBar.formatFooterLine(StatusInfo.idle("glm-5.1", 200_000L, false), 40);
        assertEquals(40, AnsiStyle.displayWidth(line), "footer should fill requested width: " + AnsiStyle.displayWidth(line));
        assertTrue(line.contains("Auto Model"), line);
    }

    @Test
    void activeStatusLineShowsPhase() {
        StatusInfo info = StatusInfo.active("glm-5.1", 200_000L, false, "plan")
                .withEnvironment("MCP 4/4", "Skill 2/2");
        String top = BottomStatusBar.formatStatusLine(info, 80);
        String bottom = BottomStatusBar.formatFooterLine(info, 80);
        assertTrue(top.contains("4 MCP servers"), top);
        assertTrue(top.contains("2 skills"), top);
        assertTrue(bottom.contains("plan"), bottom);
        assertTrue(bottom.contains("glm-5.1"), bottom);
    }

    @Test
    void statusLinesUseJLineAttributes() {
        StatusInfo info = new StatusInfo("glm-5.1", 0L, 200_000L, false, 0L);
        var lines = BottomStatusBar.formatStatusLines(info, 80);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).toString().contains("YOLO"), "status row should show current mode");
        assertTrue(lines.get(1).toAnsi().contains("[2m"), "footer row should use subtle style");
    }

    @Test
    void closeBeforeStartIsSafe() {
        Terminal terminal = Mockito.mock(Terminal.class);
        Mockito.when(terminal.getSize()).thenReturn(new Size(80, 24));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BottomStatusBar bar = new BottomStatusBar(terminal,
                new PrintStream(sink, true, StandardCharsets.UTF_8));
        bar.close();
        // 不应抛异常；不应输出 ANSI（因为 start 没调过）
        assertTrue(sink.toString(StandardCharsets.UTF_8).isEmpty());
    }

    @Test
    void startDoesNotHandWriteScrollRegion() {
        Terminal terminal = Mockito.mock(Terminal.class);
        Mockito.when(terminal.getSize()).thenReturn(new Size(80, 24));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BottomStatusBar bar = new BottomStatusBar(terminal,
                new PrintStream(sink, true, StandardCharsets.UTF_8));
        bar.start();
        try {
            String emitted = sink.toString(StandardCharsets.UTF_8);
            assertFalse(emitted.contains("[1;21r"), "dock setup must be delegated to JLine Status: " + emitted);
        } finally {
            bar.close();
        }
    }

    @Test
    void closeIsSafeAfterStart() {
        Terminal terminal = Mockito.mock(Terminal.class);
        Mockito.when(terminal.getSize()).thenReturn(new Size(80, 24));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BottomStatusBar bar = new BottomStatusBar(terminal,
                new PrintStream(sink, true, StandardCharsets.UTF_8));
        bar.start();
        bar.close();
        // 不应抛异常；mock terminal 不创建真实 JLine Status。
        assertTrue(true);
    }

    @Test
    void flushNowDoesNotPrintOutsideInputLifecycle() {
        Terminal terminal = Mockito.mock(Terminal.class);
        Mockito.when(terminal.getSize()).thenReturn(new Size(80, 24));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BottomStatusBar bar = new BottomStatusBar(terminal,
                new PrintStream(sink, true, StandardCharsets.UTF_8));
        bar.start();
        try {
            bar.update(StatusInfo.idle("glm-5.1", 200_000L, false));
            bar.flushNow();
            assertTrue(sink.toString(StandardCharsets.UTF_8).isEmpty());
        } finally {
            bar.close();
        }
    }

    @Test
    void inputLifecycleDoesNotHandWriteInlineStatusOrClearScrollback() {
        Terminal terminal = Mockito.mock(Terminal.class);
        Mockito.when(terminal.getType()).thenReturn("xterm-256color");
        Mockito.when(terminal.getSize()).thenReturn(new Size(80, 24));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        BottomStatusBar bar = new BottomStatusBar(terminal,
                new PrintStream(sink, true, StandardCharsets.UTF_8));
        bar.start();
        try {
            bar.update(StatusInfo.idle("glm-5.1", 200_000L, false));
            sink.reset();
            bar.prepareInputLine();
            String prepared = sink.toString(StandardCharsets.UTF_8);
            assertFalse(prepared.startsWith("\n\n"), "dock must not inject spacer rows under the prompt: " + prepared);
            assertFalse(prepared.contains(AnsiSeq.moveUp(3)), prepared);
            assertFalse(prepared.contains("[22;1H"), "prompt must stay in transcript flow: " + prepared);

            sink.reset();
            bar.finishInputLine();
            String finished = sink.toString(StandardCharsets.UTF_8);
            assertFalse(finished.contains(AnsiSeq.CLEAR_TO_EOS), finished);
            assertFalse(finished.contains("[22;1H"), "transcript scrolling is not manually forced anymore: " + finished);
        } finally {
            bar.close();
        }
    }

    @Test
    void formatStatusLinesRendersInPlaceMorphingDock() {
        StatusInfo info = StatusInfo.idle("glm-5.1", 200_000L, false);
        java.util.List<SlashSuggestionItem> suggestions = java.util.List.of(
                new SlashSuggestionItem("/model", "/model [name]", "切换或配置当前 LLM 模型"),
                new SlashSuggestionItem("/memory", "/memory <cmd>", "长期记忆管理"),
                new SlashSuggestionItem("/mcp", "/mcp [cmd]", "MCP 服务器管理")
        );

        // 默认第 0 项选中
        var lines0 = BottomStatusBar.formatStatusLines(info, 80, suggestions, 0);
        assertEquals(2, lines0.size(), "建议状态下底栏必须保持严格恒定的 2 行，杜绝滚屏抖动");
        assertTrue(lines0.get(0).toString().contains("▶ [1/3]"), "第一行应包含序号标识");
        assertTrue(lines0.get(0).toString().contains("/model"), "第一行应高亮当前选中项 /model");
        assertTrue(lines0.get(0).toString().contains("切换或配置当前 LLM 模型"), "第一行应包含命令说明");
        assertTrue(lines0.get(1).toString().contains("候选:"), "第二行应展示候选流");
        assertTrue(lines0.get(1).toString().contains("/memory"), "第二行应包含相邻候选项");
        assertTrue(lines0.get(1).toString().contains("↑↓ 选择"), "第二行应包含键盘交互指引");

        // 切换到第 1 项选中
        var lines1 = BottomStatusBar.formatStatusLines(info, 80, suggestions, 1);
        assertEquals(2, lines1.size());
        assertTrue(lines1.get(0).toString().contains("▶ [2/3]"), "第一行应更新为 2/3");
        assertTrue(lines1.get(0).toString().contains("/memory"), "第一行应高亮切换后的 /memory");
        assertTrue(lines1.get(0).toString().contains("长期记忆管理"), "第一行应更新说明为长期记忆管理");
        assertTrue(lines1.get(1).toString().contains("[/memory]"), "第二行应对当前项进行标记");
    }

    @Test
    void formatStatusLinesRevertsToTwoLinesWhenSuggestionsEmpty() {
        StatusInfo info = StatusInfo.idle("glm-5.1", 200_000L, false);
        var linesWithNull = BottomStatusBar.formatStatusLines(info, 80, null);
        assertEquals(2, linesWithNull.size());
        assertTrue(linesWithNull.get(0).toString().contains("Ctrl+Y"));
        assertTrue(linesWithNull.get(1).toString().contains("Auto Model"));

        var linesWithEmpty = BottomStatusBar.formatStatusLines(info, 80, java.util.List.of());
        assertEquals(2, linesWithEmpty.size());
    }

    private static String visible(String line) {
        return line;
    }
}
