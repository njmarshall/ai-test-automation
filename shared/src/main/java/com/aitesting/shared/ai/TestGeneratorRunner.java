package com.aitesting.shared.ai;

import com.aitesting.shared.config.BaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * TestGeneratorRunner is the CLI entry point for AI-powered test generation.
 *
 * It can:
 *   1. Read a local OpenAPI spec file and generate tests
 *   2. Fetch a remote OpenAPI spec URL and generate tests
 *   3. Auto-detect the PetStore spec (no args needed for the capstone demo)
 *
 * Usage — local spec file:
 *   mvn exec:java -pl shared \
 *     -Dexec.mainClass="com.aitesting.shared.ai.TestGeneratorRunner" \
 *     -Dexec.args="api/src/test/resources/petstore-openapi.json petstore"
 *
 * Usage — remote spec URL:
 *   mvn exec:java -pl shared \
 *     -Dexec.mainClass="com.aitesting.shared.ai.TestGeneratorRunner" \
 *     -Dexec.args="https://petstore.swagger.io/v2/swagger.json petstore"
 *
 * Usage — no args (defaults to PetStore remote spec):
 *   mvn exec:java -pl shared \
 *     -Dexec.mainClass="com.aitesting.shared.ai.TestGeneratorRunner"
 *
 * Environment variables required:
 *   LLM_API_KEY  — your Anthropic (or OpenAI) API key
 */
public class TestGeneratorRunner {

    private static final Logger log = LoggerFactory.getLogger(TestGeneratorRunner.class);

    // Default PetStore spec — used when no args are provided
    private static final String DEFAULT_SPEC_URL =
            "https://petstore.swagger.io/v2/swagger.json";
    private static final String DEFAULT_PROJECT = "petstore";

    public static void main(String[] args) {
        String specSource;
        String projectName;

        if (args.length >= 2) {
            specSource  = args[0];
            projectName = args[1];
        } else if (args.length == 1) {
            specSource  = args[0];
            projectName = DEFAULT_PROJECT;
        } else {
            log.info("No arguments provided — using default PetStore spec.");
            specSource  = DEFAULT_SPEC_URL;
            projectName = DEFAULT_PROJECT;
        }

        log.info("=== AI Test Generator ===");
        log.info("Spec source  : {}", specSource);
        log.info("Project name : {}", projectName);
        log.info("LLM model    : {}", BaseConfig.LLM_MODEL);
        log.info("Output dir   : {}", BaseConfig.AI_OUTPUT_DIR);

        try {
            String specContent = loadSpec(specSource);
            log.info("Spec loaded ({} chars)", specContent.length());

            TestGenerator generator = new TestGenerator();
            generator.generateFromSpecString(specContent, projectName);

            log.info("=== Generation complete ===");
            System.exit(0);

        } catch (IllegalStateException e) {
            log.error("Configuration error: {}", e.getMessage());
            log.error("Make sure LLM_API_KEY is set: export LLM_API_KEY=your_key_here");
            System.exit(2);

        } catch (IOException e) {
            log.error("I/O error during generation: {}", e.getMessage(), e);
            System.exit(1);

        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    // ── Spec loading ──────────────────────────────────────────────────────────

    /**
     * Loads the OpenAPI spec from a local file path, a remote URL, or the classpath.
     * Detection order:
     *   1. Starts with "http://" or "https://" → fetch from URL
     *   2. File exists on the filesystem → read local file
     *   3. Otherwise → attempt classpath resource load
     */
    private static String loadSpec(String source) throws IOException {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return fetchFromUrl(source);
        }

        Path localPath = Paths.get(source);
        if (Files.exists(localPath)) {
            log.info("Loading spec from local file: {}", localPath.toAbsolutePath());
            return Files.readString(localPath);
        }

        // Try classpath as last resort
        try (InputStream is = TestGeneratorRunner.class
                .getClassLoader().getResourceAsStream(source)) {
            if (is != null) {
                log.info("Loading spec from classpath: {}", source);
                return new String(is.readAllBytes());
            }
        }

        throw new IOException("Spec not found — checked URL, filesystem, and classpath: " + source);
    }

    /**
     * Fetches a remote OpenAPI spec over HTTP/HTTPS.
     * Uses Java 11+ HttpClient (no extra dependency needed).
     */
    private static String fetchFromUrl(String url) throws IOException {
        log.info("Fetching remote spec from: {}", url);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to fetch spec: HTTP "
                        + response.statusCode() + " from " + url);
            }

            log.info("Remote spec fetched successfully ({} chars)", response.body().length());
            return response.body();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted while fetching spec", e);
        }
    }
}
