package com.javacli.cli;

import com.javacli.render.Renderer;
import com.javacli.render.inline.SlashSuggestionItem;
import org.jline.reader.Buffer;
import org.jline.reader.LineReader;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 实时斜杠命令建议交互控制器。
 *
 * <p>负责接管输入缓冲区的命令变化，过滤候选列表，并在激活时拦截方向键上下切换、
 * Tab 键补全、Enter 键执行与 Esc 键取消，实现对标 OpenCode / Claude Code 的悬浮建议体验。
 */
public final class SlashSuggestionController {

    private final LineReader lineReader;
    private final AtomicReference<Renderer> rendererRef;
    private List<SlashSuggestionItem> matches = List.of();
    private int selectedIndex = 0;
    private boolean active = false;

    public SlashSuggestionController(LineReader lineReader, AtomicReference<Renderer> rendererRef) {
        this.lineReader = lineReader;
        this.rendererRef = rendererRef;
    }

    /**
     * 当输入缓冲区内容变化时被调用（通常由 Highlighter 触发）。
     */
    public synchronized void onBufferChanged(String buffer) {
        if (buffer != null && buffer.startsWith("/") && !buffer.contains(" ")) {
            List<SlashSuggestionItem> found = Main.findMatchingSlashCommands(buffer);
            if (!found.isEmpty()) {
                this.matches = found;
                this.selectedIndex = Math.max(0, Math.min(selectedIndex, matches.size() - 1));
                this.active = true;
                updateRenderer();
                return;
            }
        }
        clear();
    }

    /**
     * 当前是否处于斜杠建议活动状态。
     */
    public synchronized boolean isActive() {
        return active && matches != null && !matches.isEmpty();
    }

    /**
     * 键盘方向键下（↓）：切换到下一个候选命令。
     */
    public synchronized void selectNext() {
        if (!isActive()) {
            return;
        }
        selectedIndex = (selectedIndex + 1) % matches.size();
        updateRenderer();
        safeRedisplay();
    }

    /**
     * 键盘方向键上（↑）：切换到上一个候选命令。
     */
    public synchronized void selectPrevious() {
        if (!isActive()) {
            return;
        }
        selectedIndex = (selectedIndex - 1 + matches.size()) % matches.size();
        updateRenderer();
        safeRedisplay();
    }

    /**
     * 获取当前高亮选中的建议项。
     */
    public synchronized SlashSuggestionItem getSelectedItem() {
        if (!isActive()) {
            return null;
        }
        return matches.get(selectedIndex);
    }

    /**
     * Tab 键按下时补全当前高亮选中的命令。
     *
     * @return 如果成功消费并补全返回 true，否则 false
     */
    public synchronized boolean applyTab() {
        if (!isActive()) {
            return false;
        }
        SlashSuggestionItem item = getSelectedItem();
        if (item != null && lineReader != null) {
            Buffer buf = lineReader.getBuffer();
            buf.clear();
            buf.write(item.command() + " ");
            clear();
            safeRedisplay();
            return true;
        }
        return false;
    }

    /**
     * Enter 键按下时处理高亮选中项。
     * 如果用户仅输入了前缀，直接将完整命令填入输入缓冲区。
     *
     * @return 如果处理了返回 true，否则 false
     */
    public synchronized boolean applyEnter() {
        if (!isActive()) {
            return false;
        }
        SlashSuggestionItem item = getSelectedItem();
        if (item != null && lineReader != null) {
            String current = lineReader.getBuffer().toString().trim();
            if (item.command().startsWith(current) || current.equals("/")) {
                Buffer buf = lineReader.getBuffer();
                buf.clear();
                buf.write(item.command());
            }
            clear();
            return true;
        }
        return false;
    }

    /**
     * Esc 键按下时取消当前建议。
     */
    public synchronized boolean applyEsc() {
        if (!isActive()) {
            return false;
        }
        clear();
        return true;
    }

    /**
     * 取消并清空当前建议，恢复底栏原有状态。
     */
    public synchronized void clear() {
        if (active) {
            active = false;
            matches = List.of();
            selectedIndex = 0;
            Renderer renderer = rendererRef != null ? rendererRef.get() : null;
            if (renderer != null) {
                renderer.clearSlashSuggestions();
            }
            safeRedisplay();
        }
    }

    private void safeRedisplay() {
        if (lineReader != null && lineReader.isReading()) {
            try {
                lineReader.callWidget(LineReader.REDISPLAY);
            } catch (Exception ignored) {
            }
        }
    }

    private void updateRenderer() {
        Renderer renderer = rendererRef != null ? rendererRef.get() : null;
        if (renderer != null) {
            renderer.setSlashSuggestions(matches, selectedIndex);
        }
    }

    public synchronized int getSelectedIndex() {
        return selectedIndex;
    }

    public synchronized List<SlashSuggestionItem> getMatches() {
        return matches;
    }
}
