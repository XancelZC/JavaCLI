package com.javacli.render.inline;

/**
 * 悬浮命令提示项（用于在输入行下方展示动态过滤的命令、参数及说明）。
 *
 * @param command     用于自动补全或执行的命令文本（如 /model）
 * @param display     展示给用户的命令原型（如 /model [name]）
 * @param description 功能说明
 */
public record SlashSuggestionItem(String command, String display, String description) {

    public SlashSuggestionItem(String command, String description) {
        this(command, command, description);
    }
}
