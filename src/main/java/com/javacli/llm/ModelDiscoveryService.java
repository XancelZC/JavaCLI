package com.javacli.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 负责从远程 OpenAI 兼容端点动态拉取可用模型列表。
 *
 * <p>兼容标准的 {@code /v1/models} 与 {@code /models} 接口，以及本地 Ollama 服务的模型列表。
 */
public class ModelDiscoveryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient httpClient;

    public ModelDiscoveryService() {
        this(new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(6))
                .readTimeout(Duration.ofSeconds(10))
                .callTimeout(Duration.ofSeconds(12))
                .build());
    }

    public ModelDiscoveryService(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 向远程端点发起请求并解析返回的模型 ID 列表。
     *
     * @param baseUrl 服务基础 URL（如 https://api.deepseek.com 或 http://localhost:11434/v1）
     * @param apiKey  API Key，若无或本地免密可传 null 或空串
     * @return 排序后的可用模型 ID 列表
     * @throws IOException 网络请求失败或服务端返回错误时抛出
     */
    public List<String> fetchModels(String baseUrl, String apiKey) throws IOException {
        String normalizedUrl = normalizeBaseUrl(baseUrl);
        List<String> targetUrls = resolveCandidateUrls(normalizedUrl);

        IOException lastException = null;
        for (String url : targetUrls) {
            try {
                List<String> models = doFetch(url, apiKey);
                if (!models.isEmpty()) {
                    return models;
                }
            } catch (IOException e) {
                lastException = e;
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        return List.of();
    }

    private List<String> doFetch(String url, String apiKey) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json");

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        Request request = requestBuilder.build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                String error = body != null ? body.string() : "";
                throw new IOException("HTTP " + response.code() + " (" + response.message() + "): " + error);
            }
            if (body == null) {
                return List.of();
            }
            return parseModelsFromJson(body.string());
        }
    }

    /**
     * 解析 OpenAI 格式或 Ollama 格式的模型列表 JSON。
     */
    public static List<String> parseModelsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            Set<String> modelSet = new HashSet<>();

            // 标准 OpenAI /v1/models: { "data": [ { "id": "deepseek-chat" }, ... ] }
            JsonNode dataNode = root.path("data");
            if (dataNode.isArray()) {
                for (JsonNode item : dataNode) {
                    JsonNode idNode = item.path("id");
                    if (!idNode.isMissingNode() && !idNode.asText().isBlank()) {
                        modelSet.add(idNode.asText().trim());
                    }
                }
            }

            // Ollama 原生 /api/tags: { "models": [ { "name": "llama3:latest" }, ... ] }
            JsonNode modelsNode = root.path("models");
            if (modelsNode.isArray()) {
                for (JsonNode item : modelsNode) {
                    JsonNode nameNode = item.path("name");
                    if (!nameNode.isMissingNode() && !nameNode.asText().isBlank()) {
                        modelSet.add(nameNode.asText().trim());
                    }
                }
            }

            List<String> list = new ArrayList<>(modelSet);
            Collections.sort(list);
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    static List<String> resolveCandidateUrls(String normalizedBaseUrl) {
        List<String> urls = new ArrayList<>();
        if (normalizedBaseUrl.endsWith("/models") || normalizedBaseUrl.endsWith("/tags")) {
            urls.add(normalizedBaseUrl);
            return urls;
        }

        // 优先探测标准 OpenAI /models
        if (normalizedBaseUrl.endsWith("/v1")) {
            urls.add(normalizedBaseUrl + "/models");
            String root = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 3);
            urls.add(root + "/v1/models");
            urls.add(root + "/models");
        } else {
            urls.add(normalizedBaseUrl + "/v1/models");
            urls.add(normalizedBaseUrl + "/models");
            urls.add(normalizedBaseUrl + "/api/tags");
        }
        return urls;
    }

    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return baseUrl.trim().replaceAll("/+$", "");
    }
}
