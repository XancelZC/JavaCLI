package com.javacli.web;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebFetcherTest {

    private MockWebServer server;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void shutdown() throws IOException {
        server.shutdown();
    }

    @Test
    void fetchesSuccessfulHtml() throws IOException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body><article><h1>Hello</h1></article></body></html>"));

        WebFetcher fetcher = new WebFetcher();
        WebFetcher.RawResponse raw = fetcher.fetch(server.url("/").toString());

        assertTrue(raw.body().contains("Hello"));
        assertFalse(raw.truncated());
        assertNotNull(raw.contentType());
    }

    @Test
    void truncatesBodyOverMaxBytes() throws IOException {
        StringBuilder big = new StringBuilder("<html><body>");
        // 10KB 正文
        for (int i = 0; i < 10_000; i++) big.append("a");
        big.append("</body></html>");
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(big.toString()));

        // 设置 1KB 上限
        WebFetcher fetcher = new WebFetcher(1024);
        WebFetcher.RawResponse raw = fetcher.fetch(server.url("/").toString());

        assertTrue(raw.truncated(), "超过 maxBytes 应标记为 truncated");
        assertTrue(raw.body().length() <= 1024);
    }

    @Test
    void httpErrorThrows() {
        server.enqueue(new MockResponse().setResponseCode(404));
        WebFetcher fetcher = new WebFetcher();
        IOException ex = assertThrows(IOException.class,
                () -> fetcher.fetch(server.url("/missing").toString()));
        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    void timeoutThrows() {
        // throttleBody 模拟服务器极慢；配合 1 秒读超时触发 timeout
        server.enqueue(new MockResponse()
                .setBody("partial-")
                .throttleBody(1, 5, TimeUnit.SECONDS));

        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(1, TimeUnit.SECONDS)
                .callTimeout(2, TimeUnit.SECONDS)
                .build();
        WebFetcher fetcher = new WebFetcher(WebFetcher.DEFAULT_MAX_BYTES, client);

        assertThrows(IOException.class,
                () -> fetcher.fetch(server.url("/slow").toString()));
    }

    @Test
    void respectsCharsetFromContentType() throws IOException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody("<html><body>中文测试</body></html>"));
        WebFetcher fetcher = new WebFetcher();
        WebFetcher.RawResponse raw = fetcher.fetch(server.url("/").toString());
        assertTrue(raw.body().contains("中文测试"));
        assertEquals("UTF-8", raw.charset());
    }

    @Test
    void sendsChromeHeadersAndClientHints() throws Exception {
        server.enqueue(new MockResponse().setBody("<html><body>ok</body></html>"));
        WebFetcher fetcher = new WebFetcher();
        fetcher.fetch(server.url("/check-headers").toString());

        var recorded = server.takeRequest();
        String ua = recorded.getHeader("User-Agent");
        assertNotNull(ua);
        assertTrue(ua.contains("Chrome/"), "UA 应包含 Chrome: " + ua);
        assertNotNull(recorded.getHeader("sec-ch-ua"));
        assertNotNull(recorded.getHeader("sec-ch-ua-platform"));
        assertEquals("document", recorded.getHeader("sec-fetch-dest"));
    }

    @Test
    void injectsZhihuCookieWhenConfigured() throws Exception {
        System.setProperty("zhihu.cookie", "d_c0=mock_dc0_12345; __zse_ck=mock_zse_ck");
        try {
            server.enqueue(new MockResponse().setBody("<html><body>ok</body></html>"));
            WebFetcher fetcher = new WebFetcher();
            // MockWebServer 默认 host 是 localhost，我们测试通过 WEB_FETCH_COOKIES 支持 localhost
            System.setProperty("javacli.web.cookies", "localhost=d_c0=mock_dc0_12345");
            fetcher.fetch(server.url("/test-cookie").toString());

            var recorded = server.takeRequest();
            assertEquals("d_c0=mock_dc0_12345", recorded.getHeader("Cookie"));
        } finally {
            System.clearProperty("zhihu.cookie");
            System.clearProperty("javacli.web.cookies");
        }
    }

    @Test
    void throwsHttpChallengeExceptionOnAntiBotPage() {
        server.enqueue(new MockResponse()
                .setResponseCode(403)
                .setBody("<html><head><meta id=\"zh-zse-ck\" content=\"challenge\"></head><body>zse_ck challenge</body></html>"));

        WebFetcher fetcher = new WebFetcher();
        WebFetcher.HttpChallengeException ex = assertThrows(
                WebFetcher.HttpChallengeException.class,
                () -> fetcher.fetch(server.url("/anti-bot").toString())
        );

        assertEquals(403, ex.getStatusCode());
        assertTrue(ex.getChallengeBody().contains("zh-zse-ck"));
        assertTrue(ex.getMessage().contains("反爬验证/登录挑战"));
    }
}
