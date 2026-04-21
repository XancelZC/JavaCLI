package com.javacli.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeIndexTest {

    @Test
    void testIndexNonExistentPath() {
        CodeIndex indexer = new CodeIndex();
        CodeIndex.IndexResult result = indexer.index("/non/existent/path");
        assertEquals(0, result.chunkCount());
        assertTrue(result.message().contains("路径不存在"));
    }

    private CodeIndex createStubIndexer(CodeIndex.ProgressListener listener) {
        EmbeddingClient stubClient = new EmbeddingClient("ollama", "stub", "http://localhost", "") {
            @Override
            public float[] embed(String text) {
                return new float[384];
            }
        };
        return new CodeIndex(stubClient, listener);
    }

    @Test
    void testIndexCurrentProject() {
        System.setProperty("javacli.rag.dir", "/tmp/javacli-test-rag-index");
        CodeIndex indexer = createStubIndexer(CodeIndex.ProgressListener.noop());
        // 索引测试资源目录
        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");
        assertTrue(result.chunkCount() > 0, "应该至少索引一个代码块");
        assertTrue(result.message().contains("索引完成"));
    }

    @Test
    void reportsProgressThroughListener() {
        List<String> messages = new java.util.ArrayList<>();
        CodeIndex indexer = createStubIndexer(messages::add);

        CodeIndex.IndexResult result = indexer.index("src/test/resources/rag");

        assertTrue(result.chunkCount() > 0, "应该至少索引一个代码块");
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("🔍 开始索引")));
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("📁 发现")));
        assertTrue(messages.stream().anyMatch(message -> message.startsWith("✅ 索引完成")));
    }
}
