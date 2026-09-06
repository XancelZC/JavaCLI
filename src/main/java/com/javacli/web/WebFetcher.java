package com.javacli.web;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 基础 HTTP 抓取器：拿 URL → 字节流 → 字符串。
 *
 * 边界：
 * <ul>
 *   <li>5MB 响应体上限，超出截断（流式读，避免 OOM）</li>
 *   <li>30s 整体超时（OkHttp callTimeout）</li>
 *   <li>不处理 JS 渲染、不处理登录态 —— 那是第 13/14 期的事</li>
 *   <li>遇到 4xx/5xx 直接抛 IOException，由调用方决定如何向用户呈现</li>
 * </ul>
 *
 * 字符集解析：优先 Content-Type charset，其次 HTML meta（Jsoup 会兜底处理），
 * 全失败用 UTF-8。这里只负责拿到字符串，meta 嗅探在 {@link HtmlExtractor} 里做。
 */
public class WebFetcher {

    private static final Logger log = LoggerFactory.getLogger(WebFetcher.class);
    public static final int DEFAULT_MAX_BYTES = 5 * 1024 * 1024;
    private static final long DEFAULT_TIMEOUT_SECONDS = 30L;

    private final OkHttpClient httpClient;
    private final int maxBytes;

    public WebFetcher() {
        this(DEFAULT_MAX_BYTES);
    }

    public WebFetcher(int maxBytes) {
        this(maxBytes, new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build());
    }

    public static final String CHROME_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

    WebFetcher(int maxBytes, OkHttpClient httpClient) {
        this.maxBytes = maxBytes;
        this.httpClient = httpClient;
    }

    public RawResponse fetch(String url) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("User-Agent", CHROME_USER_AGENT)
                .header("sec-ch-ua", "\"Chromium\";v=\"128\", \"Not;A=Brand\";v=\"24\", \"Google Chrome\";v=\"128\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"macOS\"")
                .header("sec-fetch-dest", "document")
                .header("sec-fetch-mode", "navigate")
                .header("sec-fetch-site", "none")
                .header("sec-fetch-user", "?1")
                .header("upgrade-insecure-requests", "1");

        String cookie = resolveCookieForUrl(url);
        if (cookie != null && !cookie.isBlank()) {
            requestBuilder.header("Cookie", cookie);
        }

        Request request = requestBuilder.get().build();

        log.info("web_fetch: GET {}", url);
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                if (body != null) {
                    try {
                        Charset charset = resolveCharset(response, body);
                        byte[] errBytes = readBounded(body.byteStream());
                        String errText = new String(errBytes, charset);
                        if (isAntiBotChallenge(errText)) {
                            throw new HttpChallengeException(response.code(), response.message(), url, errText);
                        }
                    } catch (HttpChallengeException e) {
                        throw e;
                    } catch (Exception ignored) {
                    }
                }
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            if (body == null) {
                throw new IOException("响应体为空");
            }

            Charset charset = resolveCharset(response, body);
            byte[] bytes = readBounded(body.byteStream());
            boolean truncated = bytes.length >= maxBytes;
            String text = new String(bytes, charset);
            String contentType = response.header("Content-Type", "");
            return new RawResponse(url, text, contentType, charset.name(), truncated);
        }
    }

    public static boolean isAntiBotChallenge(String text) {
        if (text == null || text.isBlank()) return false;
        return text.contains("zh-zse-ck")
                || text.contains("zse_ck")
                || text.contains("安全验证 - 知乎")
                || text.contains("cf-browser-verification")
                || text.contains("challenge-running")
                || text.contains("cf-chl-widget");
    }

    private String resolveCookieForUrl(String url) {
        if (url == null) return null;
        try {
            okhttp3.HttpUrl httpUrl = okhttp3.HttpUrl.parse(url);
            if (httpUrl == null) return null;
            String host = httpUrl.host().toLowerCase(java.util.Locale.ROOT);
            if (host.contains("zhihu.com")) {
                String zhihuCookie = getEnvOrProp("ZHIHU_COOKIE", "JAVACLI_ZHIHU_COOKIE", "zhihu.cookie");
                if (zhihuCookie != null && !zhihuCookie.isBlank()) {
                    return zhihuCookie.trim();
                }
            }
            String allCookies = getEnvOrProp("WEB_FETCH_COOKIES", "JAVACLI_WEB_COOKIES", "javacli.web.cookies");
            if (allCookies != null && !allCookies.isBlank()) {
                for (String part : allCookies.split(";")) {
                    String trimmed = part.trim();
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        String domainKey = trimmed.substring(0, eq).trim();
                        if (host.contains(domainKey.toLowerCase(java.util.Locale.ROOT))) {
                            return trimmed.substring(eq + 1).trim();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String getEnvOrProp(String envKey, String altEnvKey, String propKey) {
        String val = System.getenv(envKey);
        if (val != null && !val.isBlank()) return val;
        val = System.getenv(altEnvKey);
        if (val != null && !val.isBlank()) return val;
        return System.getProperty(propKey);
    }

    private Charset resolveCharset(Response response, ResponseBody body) {
        try {
            if (body.contentType() != null && body.contentType().charset() != null) {
                return body.contentType().charset();
            }
        } catch (Exception ignored) {
        }
        return StandardCharsets.UTF_8;
    }

    private byte[] readBounded(InputStream input) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int n;
        while ((n = input.read(buffer)) != -1) {
            int remaining = maxBytes - total;
            if (remaining <= 0) {
                break;
            }
            int writeLen = Math.min(n, remaining);
            out.write(buffer, 0, writeLen);
            total += writeLen;
            if (total >= maxBytes) {
                break;
            }
        }
        return out.toByteArray();
    }

    public record RawResponse(String url, String body, String contentType, String charset, boolean truncated) {}

    public static class HttpChallengeException extends IOException {
        private final int statusCode;
        private final String url;
        private final String challengeBody;

        public HttpChallengeException(int statusCode, String message, String url, String challengeBody) {
            super("HTTP " + statusCode + " " + message + " (反爬验证/登录挑战)");
            this.statusCode = statusCode;
            this.url = url;
            this.challengeBody = challengeBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getUrl() {
            return url;
        }

        public String getChallengeBody() {
            return challengeBody;
        }
    }
}
