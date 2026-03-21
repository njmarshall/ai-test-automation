package com.aitesting.shared.ai;

import com.aitesting.shared.config.BaseConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * TestGenerator reads an OpenAPI/Swagger spec and uses an LLM to generate
 * Java test classes. Generated files are written to the configured output directory.
 *
 * How it works:
 *   1. Reads the OpenAPI spec file (JSON or YAML string)
 *   2. Builds a structured prompt describing what tests to generate
 *   3. Calls the configured LLM API (Anthropic Claude by default)
 *   4. Parses the LLM response to extract Java source code blocks
 *   5. Writes each class to the ai-generated/ output directory
 *
 * Usage (from command line runner or main method):
 *   TestGenerator gen = new TestGenerator();
 *   gen.generateFromSpec("api/src/test/resources/petstore-openapi.json", "petstore");
 */
public class TestGenerator {

    private static final Logger log = LoggerFactory.getLogger(TestGenerator.class);

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    public TestGenerator() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)   // LLM calls can be slow
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates Java test classes from an OpenAPI spec file.
     *
     * @param specFilePath  path to the OpenAPI JSON/YAML spec on the local filesystem
     * @param projectName   used to name the output package/folder (e.g. "petstore")
     */
    public void generateFromSpec(String specFilePath, String projectName) throws IOException {
        log.info("Reading OpenAPI spec: {}", specFilePath);
        String specContent = Files.readString(Paths.get(specFilePath));
        generateFromSpecString(specContent, projectName);
    }

    /**
     * Generates Java test classes from an OpenAPI spec string.
     *
     * @param specContent  raw OpenAPI JSON or YAML content
     * @param projectName  used to name the output package/folder
     */
    public void generateFromSpecString(String specContent, String projectName) throws IOException {
        validateConfig();

        String prompt = buildPrompt(specContent, projectName);
        log.info("Calling LLM API to generate tests for project: {}", projectName);

        String llmResponse = callLlmApi(prompt);
        log.debug("LLM response received ({} chars)", llmResponse.length());

        extractAndWriteJavaFiles(llmResponse, projectName);
    }

    // ── Prompt engineering ────────────────────────────────────────────────────

    private String buildPrompt(String specContent, String projectName) {
        return """
            You are a senior Java automation engineer specialising in API test frameworks.
            
            Generate comprehensive Java test classes for the following OpenAPI specification.
            
            Requirements:
            - Use RestAssured 5.x for HTTP calls
            - Use TestNG 7.x annotations (@Test, @BeforeClass, @AfterClass)
            - Use the shared ApiClient class: com.aitesting.shared.http.ApiClient
            - Use ResponseValidator for assertions: com.aitesting.shared.assertions.ResponseValidator
            - Use TestDataFactory for test data: com.aitesting.shared.dataprovider.TestDataFactory
            - Use AllureHelper for reporting: com.aitesting.shared.reporting.AllureHelper
            - Package: com.aitesting.api.%s.aigenerated
            - Cover: happy path (200/201), not found (404), bad request (400), unauthorized (401)
            - Cover boundary values for all numeric and string parameters
            - Add @Description and @Severity Allure annotations on each test
            - Add clear Javadoc explaining what each test validates
            
            Output ONLY valid Java source code blocks.
            Each class must start with: ```java and end with ```
            Include one class per endpoint group (e.g. PetTests, StoreTests, UserTests).
            
            OpenAPI Specification:
            %s
            """.formatted(projectName, specContent);
    }

    // ── LLM API call ──────────────────────────────────────────────────────────

    private String callLlmApi(String prompt) throws IOException {
        String requestBody = mapper.writeValueAsString(
                java.util.Map.of(
                        "model",      BaseConfig.LLM_MODEL,
                        "max_tokens", 8192,
                        "messages",   java.util.List.of(
                                java.util.Map.of("role", "user", "content", prompt)
                        )
                )
        );

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
            return root.at("/content/0/text").asText();
        }
    }

    // ── File writing ──────────────────────────────────────────────────────────

    /**
     * Parses ```java ... ``` blocks from the LLM response and writes each to disk.
     */
    private void extractAndWriteJavaFiles(String llmResponse, String projectName) throws IOException {
        String[] parts = llmResponse.split("```java");

        if (parts.length < 2) {
            log.warn("LLM response contained no Java code blocks. Raw response saved for review.");
            writeRawResponse(llmResponse, projectName);
            return;
        }

        int filesWritten = 0;
        for (int i = 1; i < parts.length; i++) {
            String block = parts[i];
            int end = block.indexOf("```");
            if (end == -1) continue;

            String javaCode = block.substring(0, end).trim();
            String className = extractClassName(javaCode);
            if (className == null) continue;

            Path outputDir = resolveOutputDir(projectName);
            Files.createDirectories(outputDir);

            Path outputFile = outputDir.resolve(className + ".java");
            Files.writeString(outputFile, javaCode);
            log.info("Generated test class written: {}", outputFile);
            filesWritten++;
        }

        log.info("TestGenerator complete — {} file(s) written for project '{}'",
                filesWritten, projectName);
    }

    /** Extracts the public class name from Java source. */
    private String extractClassName(String javaSource) {
        for (String line : javaSource.split("\n")) {
            line = line.trim();
            if (line.startsWith("public class ") || line.startsWith("public final class ")) {
                String[] tokens = line.split("\\s+");
                for (int i = 0; i < tokens.length - 1; i++) {
                    if (tokens[i].equals("class")) return tokens[i + 1].replaceAll("[{].*", "");
                }
            }
        }
        log.warn("Could not determine class name from code block — skipping");
        return null;
    }

    private Path resolveOutputDir(String projectName) {
        return Paths.get(BaseConfig.AI_OUTPUT_DIR.replace("ai-generated", "aigenerated"),
                projectName);
    }

    private void writeRawResponse(String content, String projectName) throws IOException {
        Path dir = resolveOutputDir(projectName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("llm-raw-response.txt"), content);
    }

    private void validateConfig() {
        if (BaseConfig.LLM_API_KEY == null || BaseConfig.LLM_API_KEY.isBlank()) {
            throw new IllegalStateException(
                    "LLM_API_KEY is not set. Export it as an environment variable before running TestGenerator.");
        }
    }
}
