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
import java.util.stream.Collectors;

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
 * A JSON spec with paths is split by tag ({@link OpenApiTagGroups}): one
 * request per tag, each containing only that tag's paths and asking for
 * exactly one class (e.g. PetApiGeneratedTests). Counts are summed across
 * groups. Other specs are sent whole in one request.
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

    static final String RAW_RESPONSE_FILE = "llm-raw-response.txt";

    /** Tells the model to stay within {@link FrameworkApiSummary}. */
    static final String API_RULE = "Use only these methods. Do not invent methods. "
            + "ResponseValidator is fluent: ResponseValidator.from(response).statusCode(200).hasField(...)";

    /** Setup must not hide a failed create behind a fixed id. */
    static final String SETUP_RULE = "Never fall back to a fixed id when setup fails. Fail in setup instead.";

    /** Live servers are slower than the strict default SLA. */
    static final String SLA_RULE = "Use BaseConfig.LIVE_API_SLA_MS for live servers: "
            + "withinSla(BaseConfig.LIVE_API_SLA_MS), import com.aitesting.shared.config.BaseConfig. "
            + "Do not call withinSla() with no argument.";

    /** Negative tests may only expect codes the spec lists for that operation. */
    static final String DOCUMENTED_STATUS_RULE = "Only assert a negative status code that the OpenAPI spec "
            + "documents for that operation. Do not guess.";

    /** From the first run: extract("id") gave an Integer that was cast to long (ClassCastException in setup). */
    static final String NUMERIC_EXTRACT_RULE = "To read a numeric id use response.jsonPath().getLong(\"id\"). "
            + "Do not assign ResponseValidator.extract(...) to a long or Long; JSON numbers can come back as Integer.";

    /** From the first run: GET right after DELETE on the live PetStore still returned 200. */
    static final String READ_AFTER_WRITE_RULE = "Do not assert that a read made right after a create, update "
            + "or delete already shows the change; the live server may not be consistent yet. "
            + "Assert on the response of the write itself.";

    /** Rules added after running the first generated tests against the live PetStore, in prompt order. */
    static final List<String> LIVE_RUN_RULES = List.of(
            SETUP_RULE, SLA_RULE, DOCUMENTED_STATUS_RULE, NUMERIC_EXTRACT_RULE, READ_AFTER_WRITE_RULE);

    private static final Pattern TEST_ANNOTATION = Pattern.compile("@Test\\b");
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private final LlmClient client;
    private final Path outputBaseDir;
    private final JavaCompileCheck compileCheck;
    private final int maxTokens;
    private final int maxTestsPerClass;

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
        this(client, outputBaseDir, compileCheck, BaseConfig.LLM_MAX_TOKENS, BaseConfig.LLM_MAX_TESTS_PER_CLASS);
    }

    /**
     * @param client           model client
     * @param outputBaseDir    folder that holds one sub-folder per project
     * @param compileCheck     compiles each block before it is written
     * @param maxTokens        max_tokens sent with each request ({@link BaseConfig#LLM_MAX_TOKENS})
     * @param maxTestsPerClass most @Test methods the prompt asks for per class ({@link BaseConfig#LLM_MAX_TESTS_PER_CLASS})
     */
    public TestGenerator(LlmClient client, Path outputBaseDir, JavaCompileCheck compileCheck,
                         int maxTokens, int maxTestsPerClass) {
        this.client = Objects.requireNonNull(client, "client");
        this.outputBaseDir = Objects.requireNonNull(outputBaseDir, "outputBaseDir");
        this.compileCheck = Objects.requireNonNull(compileCheck, "compileCheck");
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive: " + maxTokens);
        if (maxTestsPerClass <= 0) {
            throw new IllegalArgumentException("maxTestsPerClass must be positive: " + maxTestsPerClass);
        }
        this.maxTokens = maxTokens;
        this.maxTestsPerClass = maxTestsPerClass;
    }

    /** Prompt rule that caps the number of tests per class. */
    static String testCapRule(int maxTestsPerClass) {
        return ("Write at most %d @Test methods. Choose the most valuable: one happy path per endpoint first, "
                + "then the most important negative cases (not found, invalid input). "
                + "Do not write near-duplicate tests.").formatted(maxTestsPerClass);
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
     * @return how many code blocks were accepted and rejected
     */
    public GenerationResult generateFromSpec(String specFilePath, String projectName) throws IOException {
        log.info("Reading OpenAPI spec: {}", specFilePath);
        String specContent = Files.readString(Paths.get(specFilePath));
        return generateFromSpecString(specContent, projectName);
    }

    /**
     * Generates Java test classes from an OpenAPI spec string.
     *
     * @param specContent  raw OpenAPI JSON or YAML content
     * @param projectName  used to name the output package/folder
     * @return how many code blocks were accepted and rejected
     */
    public GenerationResult generateFromSpecString(String specContent, String projectName) throws IOException {
        List<OpenApiTagGroups.Group> groups = OpenApiTagGroups.byFirstTag(specContent);
        if (groups.isEmpty()) {
            // Not JSON, or no paths: send the whole spec in one request, as before.
            log.info("Calling LLM API to generate tests for project: {}", projectName);
            return generateOnce(buildPrompt(specContent, projectName, null), projectName,
                    "project '" + projectName + "'", RAW_RESPONSE_FILE);
        }

        log.info("Spec has {} tag group(s): {}", groups.size(),
                groups.stream().map(OpenApiTagGroups.Group::tag).toList());
        GenerationResult total = GenerationResult.NONE;
        for (OpenApiTagGroups.Group group : groups) {
            log.info("Calling LLM API for tag '{}' ({} path(s)) — asking for {}",
                    group.tag(), group.pathCount(), group.className());
            GenerationResult result = generateOnce(buildPrompt(group.specJson(), projectName, group),
                    projectName, "tag '" + group.tag() + "'",
                    "llm-raw-response-" + group.fileSafeTag() + ".txt");
            total = total.plus(result);
        }
        log.info("TestGenerator complete for project '{}' — {} group(s), {} block(s) accepted, {} rejected",
                projectName, groups.size(), total.accepted(), total.rejected());
        return total;
    }

    /** One model request: call, check the reply, write accepted classes. */
    private GenerationResult generateOnce(String prompt, String projectName, String label, String rawFile)
            throws IOException {
        LlmResponse response = callLlm(prompt);
        String llmResponse = response.text() == null ? "" : response.text();
        log.debug("LLM response for {} received ({} chars, stop_reason={}, input_tokens={}, output_tokens={})",
                label, llmResponse.length(), response.stopReason(),
                response.inputTokens(), response.outputTokens());

        if ("max_tokens".equals(response.stopReason())) {
            log.error("Rejected LLM reply for {}: stop_reason=max_tokens (reply was cut off at {} output tokens). "
                    + "No test classes written; raw response saved for review.", label, response.outputTokens());
            writeRawResponse(llmResponse, projectName, rawFile);
            log.info("Result for {}: 0 block(s) accepted, 1 rejected", label);
            return new GenerationResult(0, 1);
        }

        return extractAndWriteJavaFiles(llmResponse, projectName, label, rawFile);
    }

    // ── Prompt engineering ────────────────────────────────────────────────────

    /**
     * @param group the tag group to ask one class for, or null to ask for one class per endpoint group
     */
    private String buildPrompt(String specContent, String projectName, OpenApiTagGroups.Group group) {
        String task = group == null
                ? "Generate comprehensive Java test classes for the following OpenAPI specification."
                : ("Generate one comprehensive Java test class for the '%s' endpoints of the following "
                        + "OpenAPI specification. The specification contains only those endpoints.")
                        .formatted(group.tag());
        String classes = group == null
                ? "Include one class per endpoint group (e.g. PetTests, StoreTests, UserTests)."
                : "Output exactly one class, named %s, in exactly one ```java block.".formatted(group.className());
        return """
            You are a senior Java automation engineer specialising in API test frameworks.
            
            %s
            
            Requirements:
            - Use TestNG 7.x annotations (@Test, @BeforeClass, @AfterClass)
            - Make HTTP calls through com.aitesting.shared.http.ApiClient (it wraps RestAssured 5.x)
            - Use ResponseValidator for assertions: com.aitesting.shared.assertions.ResponseValidator
            - Use TestDataFactory for test data: com.aitesting.shared.dataprovider.TestDataFactory
            - Use AllureHelper for reporting: com.aitesting.shared.reporting.AllureHelper
            - Package: %s
            - Add @Description and @Severity Allure annotations on each test
            - Add clear Javadoc explaining what each test validates
            - %s
            %s
            
            Output ONLY valid Java source code blocks.
            Each class must start with: ```java and end with ```
            %s
            
            Framework API (public members of the shared classes, read from the code):
            %s
            
            %s
            
            OpenAPI Specification:
            %s
            """.formatted(task, expectedPackage(projectName), testCapRule(maxTestsPerClass),
                    LIVE_RUN_RULES.stream().map(r -> "- " + r).collect(Collectors.joining("\n")), classes,
                    FrameworkApiSummary.build(), API_RULE, specContent);
    }

    // ── LLM call ──────────────────────────────────────────────────────────────

    private LlmResponse callLlm(String prompt) throws IOException {
        LlmRequest request = new LlmRequest(null, prompt, BaseConfig.LLM_MODEL, maxTokens);
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
    private GenerationResult extractAndWriteJavaFiles(String llmResponse, String projectName,
                                                      String label, String rawFile) throws IOException {
        String[] parts = llmResponse.split("```java");

        if (parts.length < 2) {
            log.warn("LLM response for {} contained no Java code blocks. Raw response saved for review.", label);
            writeRawResponse(llmResponse, projectName, rawFile);
            log.info("Result for {}: 0 block(s) accepted, 1 rejected", label);
            return new GenerationResult(0, 1);
        }

        String expectedPackageLine = "package " + expectedPackage(projectName) + ";";
        int accepted = 0;
        int rejected = 0;
        for (int i = 1; i < parts.length; i++) {
            String block = parts[i];
            int end = block.indexOf("```");
            if (end == -1) {
                log.warn("Skipped code block {} for {}: no closing ``` fence", i, label);
                rejected++;
                continue;
            }

            String javaCode = block.substring(0, end).trim();
            String reason = rejectionReason(javaCode, expectedPackageLine);
            if (reason != null) {
                log.warn("Skipped code block {} for {}: {}", i, label, reason);
                rejected++;
                continue;
            }
            String className = extractClassName(javaCode);

            Path outputDir = resolveOutputDir(projectName);
            Files.createDirectories(outputDir);

            Path outputFile = outputDir.resolve(className + ".java");
            Files.writeString(outputFile, javaCode);
            int testCount = countTestMethods(javaCode);
            log.info("Generated test class written: {} ({} @Test method(s))", outputFile, testCount);
            if (testCount > maxTestsPerClass) {
                log.warn("{} has {} @Test methods, more than the cap of {} (LLM_MAX_TESTS_PER_CLASS); written anyway",
                        className, testCount, maxTestsPerClass);
            }
            accepted++;
        }

        log.info("Result for {}: {} block(s) accepted, {} rejected", label, accepted, rejected);
        return new GenerationResult(accepted, rejected);
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

    /** Counts @Test annotations in the source. */
    static int countTestMethods(String javaSource) {
        return (int) TEST_ANNOTATION.matcher(javaSource).results().count();
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

    private void writeRawResponse(String content, String projectName, String fileName) throws IOException {
        Path dir = resolveOutputDir(projectName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), content);
    }
}
