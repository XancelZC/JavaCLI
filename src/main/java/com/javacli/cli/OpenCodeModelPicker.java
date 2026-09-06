package com.javacli.cli;

import com.javacli.config.JavaCliConfig;
import com.javacli.llm.LlmClient;
import com.javacli.render.Renderer;
import com.javacli.render.inline.TerminalCapabilities;
import com.javacli.util.AnsiStyle;
import org.jline.reader.LineReader;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenCode 风格的可搜索模型选择弹窗（全量模仿 anomalyco/opencode 的 dialog-model.tsx）。
 *
 * <p>特性包含：
 * 1. 标准化的 {@code provider/model} 显示格式；
 * 2. 实时拼写过滤（Type-to-Search，边输入边高亮过滤，Backspace 删除）；
 * 3. 规范的状态标签：{@code [active]}、{@code [ready]}、{@code [local]}、{@code [needs key]}；
 * 4. 键盘上下方向键导航与平滑视口滚动；
 * 5. 一键即时切换已就绪模型，未配置 Key 原位弹出脱敏录入，末尾自带“➕ Connect new provider...”。
 */
public final class OpenCodeModelPicker {

    public static final String ACTION_CONNECT_NEW = "+ Connect new provider...";
    public static final int VIEWPORT_SIZE = 8;
    public static final int DEFAULT_BOX_WIDTH = 74;

    public record ModelEntry(
            String provider,
            String model,
            String displayName,
            String description,
            String badge,
            String badgeAnsiColor,
            boolean isAction
    ) {
        public String identifier() {
            if (isAction) {
                return displayName;
            }
            if (model == null || model.isBlank()) {
                return provider;
            }
            return provider + "/" + model;
        }

        public boolean matches(String query) {
            if (isAction) {
                return true;
            }
            if (query == null || query.isBlank()) {
                return true;
            }
            String q = query.trim().toLowerCase(Locale.ROOT);
            return identifier().toLowerCase(Locale.ROOT).contains(q)
                    || (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(q))
                    || (description != null && description.toLowerCase(Locale.ROOT).contains(q))
                    || (provider != null && provider.toLowerCase(Locale.ROOT).contains(q))
                    || (model != null && model.toLowerCase(Locale.ROOT).contains(q));
        }
    }

    private OpenCodeModelPicker() {
    }

    /**
     * 汇集当前活跃模型、本地配置的所有供应商模型以及内置主流预设模型。
     */
    public static List<ModelEntry> buildEntries(JavaCliConfig config, LlmClient activeClient) {
        List<ModelEntry> entries = new ArrayList<>();
        Map<String, ModelEntry> byId = new LinkedHashMap<>();

        String activeProvider = activeClient != null
                ? activeClient.getProviderName().toLowerCase(Locale.ROOT)
                : null;
        String activeModel = activeClient != null
                ? activeClient.getModelName()
                : null;

        // 1. 扫描 config.json 中的所有供应商配置
        if (config != null && config.getProviders() != null) {
            for (Map.Entry<String, JavaCliConfig.ProviderConfig> entry : config.getProviders().entrySet()) {
                String prov = entry.getKey().toLowerCase(Locale.ROOT);
                JavaCliConfig.ProviderConfig pc = entry.getValue();
                String m = pc.getModel() != null && !pc.getModel().isBlank() ? pc.getModel() : defaultModelForProvider(prov);
                String apiKey = config.getApiKey(prov);
                boolean hasKey = apiKey != null && !apiKey.isBlank();
                boolean isOllama = "ollama".equals(prov);
                boolean isActive = activeProvider != null && prov.equals(activeProvider) && Objects.equals(m, activeModel);

                String badge;
                String badgeColor;
                if (isActive) {
                    badge = "[active]";
                    badgeColor = "\u001b[32;1m"; // 亮绿加粗
                } else if (isOllama) {
                    badge = "[local]";
                    badgeColor = "\u001b[35;1m"; // 洋红
                } else if (hasKey) {
                    badge = "[ready]";
                    badgeColor = "\u001b[36m";   // 青色
                } else {
                    badge = "[needs key]";
                    badgeColor = "\u001b[33m";   // 黄色
                }

                ModelEntry me = new ModelEntry(prov, m, prov + "/" + m,
                        pc.getBaseUrl() != null ? pc.getBaseUrl() : "Custom endpoint",
                        badge, badgeColor, false);
                byId.put(me.identifier(), me);
            }
        }

        // 2. 如果 activeClient 尚未在列表中，优先加入
        if (activeProvider != null && activeModel != null) {
            String activeId = activeProvider + "/" + activeModel;
            if (!byId.containsKey(activeId)) {
                byId.put(activeId, new ModelEntry(
                        activeProvider,
                        activeModel,
                        activeId,
                        "Currently connected model",
                        "[active]",
                        "\u001b[32;1m",
                        false
                ));
            }
        }

        // 3. 注入标准预设模型（若尚未配置或用户想要一键配置）
        List<String[]> presets = List.of(
                new String[]{"sensenova", "deepseek-v4-flash", "SenseNova DeepSeek-V4 Flash 高性能推理"},
                new String[]{"deepseek", "deepseek-chat", "DeepSeek-V3 旗舰通用模型 (128k)"},
                new String[]{"deepseek", "deepseek-reasoner", "DeepSeek-R1 深度推理思维模型"},
                new String[]{"glm", "glm-5.1", "智谱旗舰 GLM-5.1 编码增强模型"},
                new String[]{"glm", "glm-4-flash", "智谱轻量超快 GLM-4-Flash (免费)"},
                new String[]{"kimi", "kimi-k2.6", "Moonshot Kimi 长文本上下文模型"},
                new String[]{"step", "step-3.5-flash", "阶跃星辰高速推理模型"},
                new String[]{"openai", "gpt-4o", "OpenAI GPT-4o 旗舰模型"},
                new String[]{"openai", "gpt-4o-mini", "OpenAI GPT-4o-mini 轻量模型"},
                new String[]{"ollama", "llama3", "Ollama 本地部署 Llama-3 模型"}
        );

        for (String[] preset : presets) {
            String prov = preset[0];
            String m = preset[1];
            String desc = preset[2];
            String id = prov + "/" + m;
            if (!byId.containsKey(id)) {
                String apiKey = config != null ? config.getApiKey(prov) : null;
                boolean hasKey = apiKey != null && !apiKey.isBlank();
                boolean isOllama = "ollama".equals(prov);
                boolean isActive = activeProvider != null && prov.equals(activeProvider) && Objects.equals(m, activeModel);

                String badge;
                String badgeColor;
                if (isActive) {
                    badge = "[active]";
                    badgeColor = "\u001b[32;1m";
                } else if (isOllama) {
                    badge = "[local]";
                    badgeColor = "\u001b[35;1m";
                } else if (hasKey) {
                    badge = "[ready]";
                    badgeColor = "\u001b[36m";
                } else {
                    badge = "[needs key]";
                    badgeColor = "\u001b[33m";
                }

                byId.put(id, new ModelEntry(prov, m, id, desc, badge, badgeColor, false));
            }
        }

        // 4. 构建返回列表，将 [active] 模型强制排在首位
        ModelEntry activeEntry = null;
        for (ModelEntry me : byId.values()) {
            if ("[active]".equals(me.badge())) {
                activeEntry = me;
            } else {
                entries.add(me);
            }
        }
        if (activeEntry != null) {
            entries.add(0, activeEntry);
        }

        // 5. 末尾追加连接新供应商的操作项
        entries.add(new ModelEntry(
                "",
                "",
                ACTION_CONNECT_NEW,
                "Connect OpenAI-compatible API endpoint or custom local server",
                "[action]",
                "\u001b[34;1m",
                true
        ));

        return entries;
    }

    public static List<ModelEntry> filterEntries(List<ModelEntry> allEntries, String query) {
        if (allEntries == null || allEntries.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return new ArrayList<>(allEntries);
        }
        List<ModelEntry> result = new ArrayList<>();
        for (ModelEntry entry : allEntries) {
            if (entry.matches(query)) {
                result.add(entry);
            }
        }
        // 保证操作项始终可见
        boolean hasAction = result.stream().anyMatch(ModelEntry::isAction);
        if (!hasAction) {
            for (ModelEntry entry : allEntries) {
                if (entry.isAction()) {
                    result.add(entry);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 渲染 OpenCode 样式的选择弹窗文本行。
     */
    public static List<String> renderDialogLines(List<ModelEntry> entries, int selectedIndex, String query, int terminalCols) {
        int width = Math.max(50, Math.min(DEFAULT_BOX_WIDTH, terminalCols - 2));
        int innerWidth = width - 4; // 减去左右两边的边框与留白 "│  ...  │"
        List<String> lines = new ArrayList<>();

        // 1. 顶边框
        lines.add(padBorder("┌─ Select Model ", '─', width) + "┐");

        // 2. 搜索栏
        String searchPrefix = "Search: ";
        String displayQuery = (query != null && !query.isEmpty()) ? query + "▌" : "Type to search models... ▌";
        boolean isPlaceholder = (query == null || query.isEmpty());
        String searchBody = searchPrefix + (isPlaceholder ? "\u001b[90m" + displayQuery + "\u001b[0m" : "\u001b[1m" + displayQuery + "\u001b[0m");
        lines.add(formatBoxRow(searchBody, innerWidth, width));

        // 3. 分隔线 (带有总数提示)
        int totalSelectable = Math.max(1, (int) entries.stream().filter(e -> !e.isAction()).count());
        String countTag = " Models (" + Math.min(selectedIndex + 1, entries.size()) + "/" + entries.size() + ") ";
        lines.add(padBorder("├─" + countTag, '─', width) + "┤");

        // 4. 模型候选列表视口计算
        List<ModelEntry> modelItems = new ArrayList<>();
        ModelEntry actionItem = null;
        for (ModelEntry me : entries) {
            if (me.isAction()) {
                actionItem = me;
            } else {
                modelItems.add(me);
            }
        }

        if (modelItems.isEmpty()) {
            lines.add(formatBoxRow("\u001b[90m(No models found matching \"" + (query == null ? "" : query) + "\")\u001b[0m", innerWidth, width));
        } else {
            int currentSel = Math.max(0, Math.min(selectedIndex, entries.size() - 1));
            int scrollOffset = 0;
            if (currentSel >= VIEWPORT_SIZE) {
                scrollOffset = currentSel - VIEWPORT_SIZE + 1;
            }
            int renderCount = Math.min(VIEWPORT_SIZE, modelItems.size() - scrollOffset);

            for (int i = 0; i < renderCount; i++) {
                int modelIdx = scrollOffset + i;
                ModelEntry me = modelItems.get(modelIdx);
                boolean isSelected = (modelIdx == currentSel);

                String idStr = me.identifier();
                String badgeStr = me.badge();
                String badgeColor = me.badgeAnsiColor() != null ? me.badgeAnsiColor() : "";

                // 计算中间间隙
                int idLen = AnsiStyle.displayWidth(idStr);
                int badgeLen = AnsiStyle.displayWidth(badgeStr);
                int gap = Math.max(1, innerWidth - idLen - badgeLen - 4);

                StringBuilder rowSb = new StringBuilder();
                if (isSelected) {
                    rowSb.append("\u001b[36;1m▶ \u001b[7m ").append(idStr).append(" ");
                    rowSb.append(" ".repeat(gap));
                    rowSb.append(badgeColor).append(badgeStr).append("\u001b[0m");
                } else {
                    rowSb.append("  ").append(idStr);
                    rowSb.append(" ".repeat(gap));
                    rowSb.append(badgeColor).append(badgeStr).append("\u001b[0m");
                }
                lines.add(formatBoxRow(rowSb.toString(), innerWidth, width));
            }
        }

        // 5. 操作项分隔线与操作项
        if (actionItem != null) {
            lines.add(padBorder("├", '─', width) + "┤");
            boolean isActionSelected = (selectedIndex == entries.size() - 1);
            StringBuilder actSb = new StringBuilder();
            if (isActionSelected) {
                actSb.append("\u001b[34;1m▶ \u001b[7m ").append(actionItem.displayName()).append(" \u001b[0m");
            } else {
                actSb.append("  \u001b[34m").append(actionItem.displayName()).append("\u001b[0m");
            }
            lines.add(formatBoxRow(actSb.toString(), innerWidth, width));
        }

        // 6. 底边框附带快捷键提示
        String footerTip = " ↑/↓ navigate • type to search • enter select • esc cancel ";
        lines.add(padBorder("└─" + footerTip, '─', width) + "┘");

        return lines;
    }

    private static String padBorder(String prefix, char padChar, int totalWidth) {
        int prefixWidth = AnsiStyle.displayWidth(prefix);
        int remaining = Math.max(0, totalWidth - 1 - prefixWidth);
        return prefix + String.valueOf(padChar).repeat(remaining);
    }

    private static String formatBoxRow(String content, int innerWidth, int totalWidth) {
        int contentWidth = AnsiStyle.displayWidth(content);
        int rightPad = Math.max(0, innerWidth - contentWidth);
        return "│  " + content + " ".repeat(rightPad) + "  │";
    }

    private static String defaultModelForProvider(String provider) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "deepseek" -> "deepseek-chat";
            case "glm" -> "glm-5.1";
            case "step" -> "step-3.5-flash";
            case "kimi" -> "kimi-k2.6";
            case "openai" -> "gpt-4o";
            case "ollama" -> "llama3";
            case "sensenova" -> "deepseek-v4-flash";
            default -> "default";
        };
    }

    /**
     * 交互式开启 OpenCode 弹窗循环。
     */
    public static void open(Terminal terminal,
                            LineReader lineReader,
                            Renderer renderer,
                            JavaCliConfig config,
                            AtomicReference<LlmClient> llmClientRef,
                            com.javacli.agent.Agent reactAgent,
                            com.javacli.mcp.McpServerManager mcpServerManager,
                            com.javacli.skill.SkillRegistry skillRegistry,
                            com.javacli.hitl.SwitchableHitlHandler hitlHandler) {
        PrintStream ui = renderer.stream();
        if (terminal == null) {
            ui.println("[warn] Non-interactive terminal, unable to open model picker.");
            return;
        }

        List<ModelEntry> allEntries = buildEntries(config, llmClientRef != null ? llmClientRef.get() : null);
        StringBuilder query = new StringBuilder();
        int selectedIndex = 0;
        int lastRenderedLineCount = 0;

        try {
            terminal.flush();
            Attributes originalAttributes = terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            try {
                while (true) {
                    List<ModelEntry> filtered = filterEntries(allEntries, query.toString());
                    if (selectedIndex >= filtered.size()) {
                        selectedIndex = Math.max(0, filtered.size() - 1);
                    }

                    int cols = TerminalCapabilities.safeSize(terminal).getColumns();
                    List<String> lines = renderDialogLines(filtered, selectedIndex, query.toString(), cols);

                    // 原位重绘：先抹除上一帧的行数，再打印新的一帧
                    if (lastRenderedLineCount > 0) {
                        StringBuilder clearBuf = new StringBuilder();
                        clearBuf.append("\u001b[").append(lastRenderedLineCount).append("A\r");
                        for (int i = 0; i < lastRenderedLineCount; i++) {
                            clearBuf.append("\u001b[2K\n");
                        }
                        clearBuf.append("\u001b[").append(lastRenderedLineCount).append("A\r");
                        terminal.writer().print(clearBuf.toString());
                    }

                    for (String line : lines) {
                        terminal.writer().println(line);
                    }
                    terminal.writer().flush();
                    lastRenderedLineCount = lines.size();

                    // 读取单个字符或转义序列
                    int c = reader.read();
                    if (c < 0) {
                        break;
                    }

                    // 1. 回车（选定当前项）
                    if (c == '\r' || c == '\n') {
                        // 清理弹窗行
                        clearDialogArea(terminal, lastRenderedLineCount);
                        lastRenderedLineCount = 0;

                        if (!filtered.isEmpty() && selectedIndex < filtered.size()) {
                            ModelEntry selected = filtered.get(selectedIndex);
                            dispatchSelection(terminal, lineReader, renderer, config, llmClientRef,
                                    reactAgent, mcpServerManager, skillRegistry, hitlHandler, selected);
                        }
                        break;
                    }

                    // 2. ESC 键或 Ctrl+C（取消退出）
                    if (c == 3 || c == 4) { // Ctrl+C or Ctrl+D
                        clearDialogArea(terminal, lastRenderedLineCount);
                        lastRenderedLineCount = 0;
                        ui.println("[info] Model selection cancelled.\n");
                        break;
                    }

                    if (c == 27) { // ESC 序列
                        int next = reader.read(30);
                        if (next == NonBlockingReader.READ_EXPIRED || next < 0) {
                            // 单独按下的 ESC 键
                            clearDialogArea(terminal, lastRenderedLineCount);
                            lastRenderedLineCount = 0;
                            ui.println("[info] Model selection cancelled.\n");
                            break;
                        }

                        if (next == '[' || next == 'O') {
                            int code = reader.read(30);
                            if (code == 'A') { // 方向键上 (↑)
                                selectedIndex = (selectedIndex - 1 + filtered.size()) % Math.max(1, filtered.size());
                                continue;
                            } else if (code == 'B') { // 方向键下 (↓)
                                selectedIndex = (selectedIndex + 1) % Math.max(1, filtered.size());
                                continue;
                            }
                        }
                        continue;
                    }

                    // 3. 退格键（Backspace / DEL）
                    if (c == 8 || c == 127) {
                        if (query.length() > 0) {
                            query.deleteCharAt(query.length() - 1);
                            selectedIndex = 0;
                        }
                        continue;
                    }

                    // 4. 普通字符（追加至搜索查询）
                    if (c >= 32 && c <= 126) {
                        query.append((char) c);
                        selectedIndex = 0;
                    }
                }
            } finally {
                terminal.setAttributes(originalAttributes);
            }
        } catch (Exception e) {
            ui.println("[error] Dialog error: " + e.getMessage());
        }
    }

    private static void clearDialogArea(Terminal terminal, int lineCount) {
        if (lineCount <= 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\u001b[").append(lineCount).append("A\r");
        for (int i = 0; i < lineCount; i++) {
            sb.append("\u001b[2K\n");
        }
        sb.append("\u001b[").append(lineCount).append("A\r");
        terminal.writer().print(sb.toString());
        terminal.writer().flush();
    }

    private static void dispatchSelection(Terminal terminal,
                                          LineReader lineReader,
                                          Renderer renderer,
                                          JavaCliConfig config,
                                          AtomicReference<LlmClient> llmClientRef,
                                          com.javacli.agent.Agent reactAgent,
                                          com.javacli.mcp.McpServerManager mcpServerManager,
                                          com.javacli.skill.SkillRegistry skillRegistry,
                                          com.javacli.hitl.SwitchableHitlHandler hitlHandler,
                                          ModelEntry selected) {
        PrintStream ui = renderer.stream();

        // 1. 动作项：连接新供应商向导
        if (selected.isAction()) {
            Main.runModelConfigWizard(terminal, lineReader, renderer, config, llmClientRef,
                    reactAgent, mcpServerManager, skillRegistry, hitlHandler, null);
            return;
        }

        // 2. 已经是活跃模型
        if ("[active]".equals(selected.badge())) {
            ui.println("✓ Already using model: \u001b[1m" + selected.identifier() + "\u001b[0m\n");
            return;
        }

        // 3. 需配置 API Key
        if ("[needs key]".equals(selected.badge())) {
            ui.println("API key not configured for \u001b[1m" + selected.identifier() + "\u001b[0m.");
            String prompt = "Enter API key for " + selected.provider() + ": ";
            String inputKey = Main.readMaskedInput(terminal, lineReader, prompt);
            if (inputKey == null || inputKey.isBlank()) {
                ui.println("[info] Cancelled.\n");
                return;
            }
            config.setApiKey(selected.provider(), inputKey.trim());
            if (selected.model() != null && !selected.model().isBlank()) {
                config.setModel(selected.provider(), selected.model());
            }
            if (config.getBaseUrl(selected.provider()) == null && !Main.isStandardWellKnownProvider(selected.provider())) {
                String urlPrompt = "Enter Base URL for " + selected.provider() + " [default: https://api.openai.com/v1]: ";
                String inputUrl = Main.readInputLine(terminal, lineReader, urlPrompt);
                String finalUrl = (inputUrl == null || inputUrl.isBlank()) ? "https://api.openai.com/v1" : inputUrl.trim();
                config.setBaseUrl(selected.provider(), finalUrl);
            }
            try {
                config.save();
            } catch (Exception ignored) {}
            Main.switchModelDirect(terminal, lineReader, renderer, config, llmClientRef, reactAgent,
                    mcpServerManager, skillRegistry, hitlHandler, selected.identifier());
            return;
        }

        // 4. [ready] 或 [local]：一键切换
        Main.switchModelDirect(terminal, lineReader, renderer, config, llmClientRef, reactAgent,
                mcpServerManager, skillRegistry, hitlHandler, selected.identifier());
    }
}
