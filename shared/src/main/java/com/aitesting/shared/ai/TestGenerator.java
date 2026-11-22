package com.aitesting.shared.ai;

import com.aitesting.shared.config.BaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TestGenerator reads an OpenAPI/Swagger spec and uses an LLM to generate
 * Java test classes. Generated files are written to the configured output directory.
 *
 * How it works:
 *   1. Reads the OpenAPI spec file (JSON or YAML string)
 *   2. Builds a structured prompt describing what tests to generate
 *   3. Calls the model through an {@link LlmClient} (Anthropic Claude by default)
 *   4. Parses the LLM response to extract Java source code blocks
 *   5. Writes each class to the ai-generated/ output directory
 *
 * Usage (from command line runner or main method):
 *   TestGenerator gen = new TestGenerator();
 *   gen.generateFromSpec("api/src/test/resources/petstore-openapi.json", "petstore");
 *
 * Unit tests pass a fake {@link LlmClient} and a temp output folder:
 *   new TestGenerator(fakeClient, tempDir)
 *
 * Every accepted block must also compile ({@link JavaCompileCheck}); the
 * check can be replaced through {@link #TestGenerator(LlmClient, Path, JavaCompileCheck)}.
 */
public class TestGenerator {

    private static final Logger log = LoggerFactory.getLogger(TestGenerator.class);

    static final int MAX_TOKENS = 8192;
    static final String RAW_RESPONSE_FILE = "llm-raw-response.txt";

    private static final Pattern TEST_ANNOTATION = Pattern.compile("@Test\\b");
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private final LlmClient client;
    private final Path outputBaseDir;
    private final JavaCompileCheck compileCheck;

    /** Production constructor: real Anthropic client, output folder from {@link BaseConfig#AI_OUTPUT_DIR}. */
    public TestGenerator() {
        this(new AnthropicLlmClient());
    }

    /** Uses the given client and the output folder from {@link BaseConfig#AI_OUTPUT_DIR}. */
    public TestGenerator(LlmClient client) {
        this(client, defaultOutputBaseDir());
    }

    /**
     * @param client        model client
     * @param outputBaseDir folder that holds one sub-folder per project
     */
    public TestGenerator(LlmClient client, Path outputBaseDir) {
        this(client, outputBaseDir, new JavaCompileCheck());
    }

    /**
     * @param client        model client
     * @param outputBaseDir folder that holds one sub-folder per project
     * @param compileCheck  compiles each block before it is written
     */
    public TestGenerator(LlmClient client, Path outputBaseDir, JavaCompileCheck compileCheck) {
        this.client = Objects.requireNonNull(client, "client");
        this.outputBaseDir = Objects.requireNonNull(outputBaseDir, "outputBaseDir");
        this.compileCheck = Objects.requireNonNull(compileCheck, "compileCheck");
    }

    /** The package generated classes must declare; matches the folder they are written to. */
    static String expectedPackage(String projectName) {
        return "com.aitesting.api.aigenerated." + projectName;
    }

    private static Path defaultOutputBaseDir() {
        return Paths.get(BaseConfig.AI_OUTPUT_DIR.replace("ai-generated", "aigenerated"));
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
        String prompt = buildPrompt(specContent, projectName);
        log.info("Calling LLM API to generate tests for project: {}", projectName);

        LlmResponse response = callLlm(prompt);
        String llmResponse = response.text() == null ? "" : response.text();
        log.debug("LLM response received ({} chars, stop_reason={}, input_tokens={}, output_tokens={})",
                llmResponse.length(), response.stopReason(),
                response.inputTokens(), response.outputTokens());

        if ("max_tokens".equals(response.stopReason())) {
            log.error("Rejected LLM reply: stop_reason=max_tokens (reply was cut off at {} output tokens). "
                    + "No test classes written; raw response saved for review.", response.outputTokens());
            writeRawResponse(llmResponse, projectName);
            return;
        }

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
            - Package: %s
            - Cover: happy path (200/201), not found (404), bad request (400), unauthorized (401)
            - Cover boundary values for all numeric and string parameters
            - Add @Description and @Severity Allure annotations on each test
            - Add clear Javadoc explaining what each test validates
            
            Output ONLY valid Java source code blocks.
            Each class must start with: ```java and end with ```
            Include one class per endpoint group (e.g. PetTests, StoreTests, UserTests).
            
            OpenAPI Specification:
            %s
            """.formatted(expectedPackage(projectName), specContent);
    }

    // ── LLM call ──────────────────────────────────────────────────────────────

    private LlmResponse callLlm(String prompt) throws IOException {
        LlmRequest request = new LlmRequest(null, prompt, BaseConfig.LLM_MODEL, MAX_TOKENS);
        try {
            return client.complete(request);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // ── File writing ──────────────────────────────────────────────────────────

    /**
     * Parses ```java ... ``` blocks from the LLM response, checks each one (including that it compiles),
     * and writes the accepted ones to disk.
     */
    private void extractAndWriteJavaFiles(String llmResponse, String projectName) throws IOException {
        String[] parts = llmResponse.split("```java");

        if (parts.length < 2) {
            log.warn("LLM response contained no Java code blocks. Raw response saved for review.");
            writeRawResponse(llmResponse, projectName);
            return;
        }

        String expectedPackageLine = "package " + expectedPackage(projectName) + ";";
        int accepted = 0;
        int rejected = 0;
        for (int i = 1; i < parts.length; i++) {
            String block = parts[i];
            int end = block.indexOf("```");
            if (end == -1) {
                log.warn("Skipped code block {}: no closing ``` fence", i);
                rejected++;
                continue;
            }

            String javaCode = block.substring(0, end).trim();
            String reason = rejectionReason(javaCode, expectedPackageLine);
            if (reason != null) {
                log.warn("Skipped code block {}: {}", i, reason);
                rejected++;
                continue;
            }
            String className = extractClassName(javaCode);

            Path outputDir = resolveOutputDir(projectName);
            Files.createDirectories(outputDir);

            Path outputFile = outputDir.resolve(className + ".java");
            Files.writeString(outputFile, javaCode);
            log.info("Generated test class written: {}", outputFile);
            accepted++;
        }

        log.info("TestGenerator complete for project '{}' — {} block(s) accepted, {} rejected",
                projectName, accepted, rejected);
    }

    /** Returns why a code block must be skipped, or null if it is acceptable. */
    private String rejectionReason(String javaCode, String expectedPackageLine) {
        String packageLine = extractPackageLine(javaCode);
        if (!expectedPackageLine.equals(packageLine)) {
            return "package line is '" + packageLine + "', expected '" + expectedPackageLine + "'";
        }
        if (!TEST_ANNOTATION.matcher(javaCode).find()) {
            return "no @Test annotation";
        }
        String className = extractClassName(javaCode);
        if (className == null) {
            return "no public class name found";
        }
        List<Diagnostic<? extends JavaFileObject>> errors = compileCheck.errors(className, javaCode);
        if (!errors.isEmpty()) {
            Diagnostic<? extends JavaFileObject> first = errors.get(0);
            // javac messages span several lines ("cannot find symbol\n  symbol: ..."); keep them on one log line
            String message = first.getMessage(Locale.ROOT).strip().replaceAll("\\s*\\R\\s*", "; ");
            return "does not compile: line " + first.getLineNumber() + ": " + message;
        }
        return null;
    }

    /** Returns the first line starting with "package ", trimmed, or null. */
    private String extractPackageLine(String javaSource) {
        for (String line : javaSource.split("\n")) {
            line = line.trim();
            if (line.startsWith("package ")) return line;
        }
        return null;
    }

    /** Extracts the public class name from Java source, or null if none is a valid identifier. */
    private String extractClassName(String javaSource) {
        for (String line : javaSource.split("\n")) {
            line = line.trim();
            if (line.startsWith("public class ") || line.startsWith("public final class ")) {
                String[] tokens = line.split("\\s+");
                for (int i = 0; i < tokens.length - 1; i++) {
                    if (tokens[i].equals("class")) {
                        String name = tokens[i + 1].replaceAll("[{<].*", "");
                        return JAVA_IDENTIFIER.matcher(name).matches() ? name : null;
                    }
                }
            }
        }
        return null;
    }

    private Path resolveOutputDir(String projectName) {
        return outputBaseDir.resolve(projectName);
    }

    private void writeRawResponse(String content, String projectName) throws IOException {
        Path dir = resolveOutputDir(projectName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(RAW_RESPONSE_FILE), content);
    }
}
