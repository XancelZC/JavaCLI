package com.javacli.cli;

import com.javacli.config.JavaCliConfig;
import com.javacli.mcp.mention.AtMentionCompleter;
import com.javacli.mcp.resources.McpResourceDescriptor;
import com.javacli.skill.Skill;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

final class JavaCliCompleter implements Completer {
    private final Supplier<List<McpResourceDescriptor>> resourceSupplier;
    private final Supplier<List<Skill>> skillSupplier;

    JavaCliCompleter(Supplier<List<McpResourceDescriptor>> resourceSupplier) {
        this(resourceSupplier, List::of);
    }

    JavaCliCompleter(Supplier<List<McpResourceDescriptor>> resourceSupplier,
                    Supplier<List<Skill>> skillSupplier) {
        this.resourceSupplier = resourceSupplier;
        this.skillSupplier = skillSupplier == null ? List::of : skillSupplier;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (line == null || candidates == null) {
            return;
        }
        String input = line.line() == null ? "" : line.line();
        String word = line.word() == null ? "" : line.word();
        if (word.startsWith("@image:")) {
            completeImagePath(line, candidates);
            return;
        }
        if (input.startsWith("/")) {
            completeSlashCommand(line, candidates);
            return;
        }
        new AtMentionCompleter(resourceSupplier).complete(reader, line, candidates);
        completeLocalPathMention(line, candidates);
    }

    private void completeSlashCommand(ParsedLine line, List<Candidate> candidates) {
        String input = line.line() == null ? "" : line.line();
        if (completeModel(input, candidates)
                || completeMcp(input, candidates)
                || completeSkill(input, candidates)
                || completeTask(input, candidates)
                || completeBrowser(input, candidates)
                || completeSnapshot(input, candidates)) {
            return;
        }

        int cursor = Math.max(0, Math.min(line.cursor(), input.length()));
        String prefix = input.substring(0, cursor);
        String word = line.word() == null ? "" : line.word();
        int replacementStart = Math.max(0, prefix.length() - word.length());

        for (Main.SlashCommandHint hint : Main.slashCommandHints()) {
            String command = hint.insertText();
            if (!command.startsWith(prefix)) {
                continue;
            }
            String value = command.substring(Math.min(replacementStart, command.length()));
            candidates.add(new Candidate(
                    value,
                    hint.display(),
                    "JavaCLI 命令",
                    hint.description(),
                    null,
                    null,
                    true
            ));
        }
    }

    private boolean completeModel(String input, List<Candidate> candidates) {
        if (input.length() > 6 && !input.regionMatches(true, 0, "/model", 0, 6)) {
            return false;
        }
        if (!input.equalsIgnoreCase("/model") && !input.regionMatches(true, 0, "/model ", 0, 7)) {
            return false;
        }
        String payload = input.length() <= 7 ? "" : input.substring(7);
        String[] parts = payload.trim().isEmpty() ? new String[0] : payload.trim().split("\\s+");

        // 如果用户正在键入 key/url/config/show 的子参数，提供 provider 补全
        if (parts.length >= 1) {
            String sub = parts[0].toLowerCase(Locale.ROOT);
            if (List.of("key", "url", "config", "show", "set-key", "set-url", "info").contains(sub)) {
                if (parts.length == 1 && payload.endsWith(" ")) {
                    addProviderCandidates(candidates, "");
                    return true;
                } else if (parts.length == 2 && !payload.endsWith(" ")) {
                    addProviderCandidates(candidates, parts[1]);
                    return true;
                }
            }
        }

        JavaCliConfig config = JavaCliConfig.load();
        boolean glmReady = config.getApiKey("glm") != null && !config.getApiKey("glm").isBlank();
        boolean deepseekReady = config.getApiKey("deepseek") != null && !config.getApiKey("deepseek").isBlank();
        boolean stepReady = config.getApiKey("step") != null && !config.getApiKey("step").isBlank();
        boolean kimiReady = config.getApiKey("kimi") != null && !config.getApiKey("kimi").isBlank();
        boolean openaiReady = config.getApiKey("openai") != null && !config.getApiKey("openai").isBlank();

        addMatching(candidates, "模型指令", payload,
                option("key ", "设置指定供应商的 API Key", "/model key <provider> <key>"),
                option("url ", "设置指定供应商的端点 Base URL", "/model url <provider> <url>"),
                option("config ", "配置供应商参数 (URL / Key / Model)", "/model config <provider> ..."),
                option("show", "查看当前各供应商配置概况与状态", "/model show"));

        addMatching(candidates, "模型", payload,
                option("glm-5.1", glmReady ? "GLM-5.1 长上下文 (✅ 已就绪)" : "GLM-5.1 (需 GLM_API_KEY)"),
                option("glm-5v-turbo", glmReady ? "GLM-5V 多模态识图 (✅ 已就绪)" : "GLM-5V (需 GLM_API_KEY)"),
                option("deepseek", deepseekReady ? "DeepSeek (✅ 已就绪)" : "DeepSeek (需 DEEPSEEK_API_KEY)"),
                option("step", stepReady ? "StepFun / 自定义OpenAI (✅ 已就绪)" : "StepFun (需 STEP_API_KEY)"),
                option("kimi", kimiReady ? "Kimi / 自定义OpenAI (✅ 已就绪)" : "Kimi (需 KIMI_API_KEY)"),
                option("openai", openaiReady ? "OpenAI 官方 (✅ 已就绪)" : "OpenAI (需 OPENAI_API_KEY)"),
                option("ollama", "Ollama 本地大模型 (✅ 默认 11434)"),
                option("custom", "自定义 OpenAI 兼容端点"));
        return true;
    }

    private void addProviderCandidates(List<Candidate> candidates, String prefix) {
        addMatching(candidates, "供应商", prefix,
                option("deepseek", "DeepSeek 官方或兼容端点"),
                option("glm", "智谱 GLM 开放平台"),
                option("kimi", "Moonshot Kimi 端点"),
                option("step", "StepFun 阶跃星辰端点"),
                option("openai", "OpenAI 官方端点"),
                option("ollama", "本地 Ollama 端点 (默认 11434)"),
                option("custom", "自定义 OpenAI 兼容端点 (OneAPI/vLLM/etc)"));
    }

    private boolean completeMcp(String input, List<Candidate> candidates) {
        if (!input.equalsIgnoreCase("/mcp") && !input.regionMatches(true, 0, "/mcp ", 0, 5)) {
            return false;
        }
        String payload = input.length() <= 5 ? "" : input.substring(5);
        String[] parts = payload.trim().isEmpty() ? new String[0] : payload.trim().split("\\s+");
        if (parts.length <= 1 && !payload.endsWith(" ")) {
            addMatching(candidates, "MCP 命令", payload,
                    option("restart ", "重启 MCP server", "/mcp restart <name>"),
                    option("logs ", "查看 MCP server stderr", "/mcp logs <name>"),
                    option("disable ", "禁用 MCP server", "/mcp disable <name>"),
                    option("enable ", "启用 MCP server", "/mcp enable <name>"),
                    option("resources ", "查看 MCP resources", "/mcp resources <name>"),
                    option("prompts ", "查看 MCP prompts", "/mcp prompts <name>"));
            return true;
        }
        String sub = parts.length == 0 ? "" : parts[0].toLowerCase();
        if (List.of("restart", "logs", "disable", "enable", "resources", "prompts").contains(sub)) {
            String prefix = payload.endsWith(" ") ? "" : parts.length >= 2 ? parts[parts.length - 1] : "";
            addServerCandidates(candidates, prefix);
            return true;
        }
        return true;
    }

    private boolean completeSkill(String input, List<Candidate> candidates) {
        if (!input.equalsIgnoreCase("/skill") && !input.regionMatches(true, 0, "/skill ", 0, 7)) {
            return false;
        }
        String payload = input.length() <= 7 ? "" : input.substring(7);
        String[] parts = payload.trim().isEmpty() ? new String[0] : payload.trim().split("\\s+");
        if (parts.length <= 1 && !payload.endsWith(" ")) {
            addMatching(candidates, "Skill 命令", payload,
                    option("list", "查看 skill 列表"),
                    option("show ", "查看 SKILL.md 全文"),
                    option("on ", "启用 skill"),
                    option("off ", "禁用 skill"),
                    option("reload", "重新扫描 skill 目录"));
            return true;
        }
        String sub = parts.length == 0 ? "" : parts[0].toLowerCase();
        if (List.of("show", "on", "off").contains(sub)) {
            String prefix = payload.endsWith(" ") ? "" : parts.length >= 2 ? parts[parts.length - 1] : "";
            addSkillCandidates(candidates, prefix);
            return true;
        }
        return true;
    }

    private boolean completeTask(String input, List<Candidate> candidates) {
        if (!input.equalsIgnoreCase("/task") && !input.regionMatches(true, 0, "/task ", 0, 6)) {
            return false;
        }
        String payload = input.length() <= 6 ? "" : input.substring(6);
        addMatching(candidates, "后台任务", payload,
                option("list", "查看后台任务列表"),
                option("add ", "提交后台任务"),
                option("cancel ", "取消后台任务"),
                option("log ", "查看后台任务结果"));
        return true;
    }

    private boolean completeBrowser(String input, List<Candidate> candidates) {
        if (!input.equalsIgnoreCase("/browser") && !input.regionMatches(true, 0, "/browser ", 0, 9)) {
            return false;
        }
        String payload = input.length() <= 9 ? "" : input.substring(9);
        addMatching(candidates, "浏览器", payload,
                option("status", "查看浏览器会话状态"),
                option("connect", "复用登录态 Chrome"),
                option("tabs", "查看 shared 模式真实 Chrome tab"),
                option("disconnect", "切回 isolated 模式"));
        return true;
    }

    private boolean completeSnapshot(String input, List<Candidate> candidates) {
        if (!input.equalsIgnoreCase("/snapshot") && !input.regionMatches(true, 0, "/snapshot ", 0, 10)) {
            return false;
        }
        String payload = input.length() <= 10 ? "" : input.substring(10);
        addMatching(candidates, "快照", payload,
                option("status", "查看 Side-Git 快照状态"),
                option("clean", "清理当前项目快照"));
        return true;
    }

    private void completeImagePath(ParsedLine line, List<Candidate> candidates) {
        String word = line.word() == null ? "" : line.word();
        String prefix = word.substring("@image:".length());
        boolean angle = prefix.startsWith("<");
        String pathPrefix = angle ? prefix.substring(1) : prefix;
        for (Candidate candidate : localPathCandidates(pathPrefix, "图片路径")) {
            String value = angle ? "@image:<" + candidate.value() : "@image:" + candidate.value();
            candidates.add(new Candidate(value, value, candidate.group(), candidate.descr(), null, null, true));
        }
    }

    private void completeLocalPathMention(ParsedLine line, List<Candidate> candidates) {
        String word = line.word() == null ? "" : line.word();
        if (!word.startsWith("@") || word.startsWith("@image:") || word.startsWith("@clipboard")) {
            return;
        }
        String prefix = word.substring(1);
        if (prefix.contains(":")) {
            return;
        }
        boolean angle = prefix.startsWith("<");
        String pathPrefix = angle ? prefix.substring(1) : prefix;
        for (Candidate candidate : localPathCandidates(pathPrefix, "本地路径")) {
            String value = angle ? "@<" + candidate.value() : "@" + candidate.value();
            candidates.add(new Candidate(value, value, candidate.group(), candidate.descr(), null, null, true));
        }
    }

    private void addServerCandidates(List<Candidate> candidates, String prefix) {
        List<String> servers = resourceSupplier.get().stream()
                .map(McpResourceDescriptor::serverName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .toList();
        for (String server : servers) {
            if (matches(server, prefix)) {
                candidates.add(new Candidate(server, server, "MCP server", "来自 resource cache", null, null, true));
            }
        }
    }

    private void addSkillCandidates(List<Candidate> candidates, String prefix) {
        for (Skill skill : skillSupplier.get()) {
            if (skill == null || !matches(skill.name(), prefix)) {
                continue;
            }
            candidates.add(new Candidate(
                    skill.name(),
                    skill.name(),
                    "Skill",
                    skill.description(),
                    null,
                    null,
                    true
            ));
        }
    }

    private static List<Candidate> localPathCandidates(String prefix, String group) {
        java.nio.file.Path base;
        String filePrefix;
        if (prefix == null || prefix.isBlank()) {
            base = java.nio.file.Path.of(".");
            filePrefix = "";
        } else {
            java.nio.file.Path typed = java.nio.file.Path.of(prefix);
            base = typed.getParent() == null ? java.nio.file.Path.of(".") : typed.getParent();
            filePrefix = typed.getFileName() == null ? "" : typed.getFileName().toString();
        }
        if (!java.nio.file.Files.isDirectory(base)) {
            return List.of();
        }
        List<Candidate> result = new ArrayList<>();
        try (var stream = java.nio.file.Files.list(base)) {
            stream.sorted().limit(50).forEach(path -> {
                String name = path.getFileName().toString();
                if (!name.startsWith(filePrefix)) {
                    return;
                }
                boolean dir = java.nio.file.Files.isDirectory(path);
                String value = base.equals(java.nio.file.Path.of("."))
                        ? name
                        : base.resolve(name).toString();
                if (dir) {
                    value += java.io.File.separator;
                }
                result.add(new Candidate(value, value, group, dir ? "目录" : "文件", null, null, !dir));
            });
        } catch (Exception ignored) {
            return List.of();
        }
        return result;
    }

    private static CommandOption option(String value, String description) {
        return new CommandOption(value, description, null);
    }

    private static CommandOption option(String value, String description, String display) {
        return new CommandOption(value, description, display);
    }

    private static void addMatching(List<Candidate> candidates, String group, String prefix, CommandOption... options) {
        for (CommandOption option : options) {
            if (matches(option.value(), prefix)) {
                candidates.add(new Candidate(
                        option.value(),
                        option.display() == null ? option.value().trim() : option.display(),
                        group,
                        option.description(),
                        null,
                        null,
                        option.value().endsWith(" ")
                ));
            }
        }
    }

    private static boolean matches(String value, String prefix) {
        return prefix == null || prefix.isBlank() || value.toLowerCase().startsWith(prefix.toLowerCase());
    }

    private record CommandOption(String value, String description, String display) {
    }
}
