package com.javacli.cli;

import com.javacli.render.Renderer;
import org.jline.reader.Buffer;
import org.jline.reader.LineReader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlashSuggestionControllerTest {

    @Test
    void activatesOnSlashAndCyclesCandidates() {
        LineReader reader = Mockito.mock(LineReader.class);
        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<Renderer> rendererRef = new AtomicReference<>(renderer);

        SlashSuggestionController controller = new SlashSuggestionController(reader, rendererRef);
        assertFalse(controller.isActive());

        // 输入 '/' 激活建议
        controller.onBufferChanged("/");
        assertTrue(controller.isActive());
        assertEquals(0, controller.getSelectedIndex());
        assertNotNull(controller.getSelectedItem());
        int total = controller.getMatches().size();
        assertTrue(total > 1);

        // 下键切换
        controller.selectNext();
        assertEquals(1, controller.getSelectedIndex());

        // 上键切回
        controller.selectPrevious();
        assertEquals(0, controller.getSelectedIndex());

        // 循环到末尾
        controller.selectPrevious();
        assertEquals(total - 1, controller.getSelectedIndex());

        // 输入空格终止建议
        controller.onBufferChanged("/model ");
        assertFalse(controller.isActive());
        verify(renderer).clearSlashSuggestions();
    }

    @Test
    void applyTabCompletesSelectedCommandWithSpace() {
        LineReader reader = Mockito.mock(LineReader.class);
        Buffer buffer = Mockito.mock(Buffer.class);
        when(reader.getBuffer()).thenReturn(buffer);

        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<Renderer> rendererRef = new AtomicReference<>(renderer);

        SlashSuggestionController controller = new SlashSuggestionController(reader, rendererRef);
        controller.onBufferChanged("/mod");
        assertTrue(controller.isActive());

        String cmd = controller.getSelectedItem().command();
        boolean tabApplied = controller.applyTab();

        assertTrue(tabApplied);
        verify(buffer).clear();
        verify(buffer).write(cmd + " ");
        assertFalse(controller.isActive());
    }

    @Test
    void applyEnterAlignsBufferPrefix() {
        LineReader reader = Mockito.mock(LineReader.class);
        Buffer buffer = Mockito.mock(Buffer.class);
        when(reader.getBuffer()).thenReturn(buffer);
        when(buffer.toString()).thenReturn("/m");

        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<Renderer> rendererRef = new AtomicReference<>(renderer);

        SlashSuggestionController controller = new SlashSuggestionController(reader, rendererRef);
        controller.onBufferChanged("/m");
        assertTrue(controller.isActive());

        String selectedCmd = controller.getSelectedItem().command();
        boolean enterHandled = controller.applyEnter();

        assertTrue(enterHandled);
        verify(buffer).clear();
        verify(buffer).write(selectedCmd);
        assertFalse(controller.isActive());
    }

    @Test
    void clearDoesNotCallWidgetWhenNotReading() {
        LineReader reader = Mockito.mock(LineReader.class);
        when(reader.isReading()).thenReturn(false); // 模拟 readLine 已返回，非读取状态

        Renderer renderer = Mockito.mock(Renderer.class);
        AtomicReference<Renderer> rendererRef = new AtomicReference<>(renderer);

        SlashSuggestionController controller = new SlashSuggestionController(reader, rendererRef);
        controller.onBufferChanged("/m");
        assertTrue(controller.isActive());

        // clear 在 readLine 返回后的 finally 中被触发，不得调用 callWidget
        controller.clear();
        assertFalse(controller.isActive());
        Mockito.verify(reader, Mockito.never()).callWidget(Mockito.anyString());
        Mockito.verify(renderer).clearSlashSuggestions();
    }
}
