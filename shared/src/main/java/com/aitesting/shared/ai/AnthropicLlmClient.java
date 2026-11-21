package com.aitesting.shared.ai;

import com.aitesting.shared.config.BaseConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@link LlmClient} backed by the Anthropic Messages API over OkHttp.
 *
 * Reads the endpoint and key from {@link BaseConfig#LLM_API_URL} and
 * {@link BaseConfig#LLM_API_KEY}. Throws {@link IllegalStateException} if the
 * key is not set, before any HTTP call is made.
 */
public class AnthropicLlmClient implements LlmClient {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public AnthropicLlmClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)   // LLM calls can be slow
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public LlmResponse complete(LlmRequest llmRequest) {
        validateConfig();
        try {
            return call(llmRequest);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private LlmResponse call(LlmRequest llmRequest) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llmRequest.model());
        body.put("max_tokens", llmRequest.maxTokens());
        if (llmRequest.system() != null && !llmRequest.system().isBlank()) {
            body.put("system", llmRequest.system());
        }
        body.put("messages", List.of(Map.of("role", "user", "content", llmRequest.user())));
        String requestBody = mapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(BaseConfig.LLM_API_URL)
                .addHeader("x-api-key",         BaseConfig.LLM_API_KEY)
                .addHeader("anthropic-version",  "2023-06-01")
                .addHeader("content-type",       "application/json")
                .post(RequestBody.create(requestBody, JSON_MEDIA))
                .build();

        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LLM API call failed: HTTP " + response.code()
                        + " — " + response.body().string());
            }
            String bodyStr = response.body().string();
            JsonNode root = mapper.readTree(bodyStr);
            return new LlmResponse(
                    root.at("/content/0/text").asText(),
                    root.path("stop_reason").asText(null),
                    root.path("usage").path("input_tokens").asInt(0),
                    root.path("usage").path("output_tokens").asInt(0));
        }
    }

    private void validateConfig() {
        if (BaseConfig.LLM_API_KEY == null || BaseConfig.LLM_API_KEY.isBlank()) {
            throw new IllegalStateException(
                    "LLM_API_KEY is not set. Export it as an environment variable before running TestGenerator.");
        }
    }
}
