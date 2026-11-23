package com.aitesting.shared.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aitesting.shared.config.BaseConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestGeneratorTest — unit tests for TestGenerator with a scripted
 * FakeLlmClient. No real API is called; output goes to a @TempDir.
 */
@DisplayName("TestGenerator")
class TestGeneratorTest {

    private static final String PROJECT = "petstore";
    private static final String PACKAGE = "com.aitesting.api.aigenerated.petstore";
    private static final String SPEC = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"Spec-Marker-42\"}}";

    @TempDir
    Path tempDir;

    /** Returns a scripted reply and records every request it received. */
    static final class FakeLlmClient implements LlmClient {
        private final Function<LlmRequest, LlmResponse> replies;
        final List<LlmRequest> requests = new ArrayList<>();
        LlmRequest lastRequest;

        FakeLlmClient(LlmResponse reply) {
            this(request -> reply);
        }

        FakeLlmClient(Function<LlmRequest, LlmResponse> replies) {
            this.replies = replies;
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            requests.add(request);
            this.lastRequest = request;
            return replies.apply(request);
        }
    }

    private static LlmResponse reply(String text) {
        return reply(text, "end_turn");
    }

    private static LlmResponse reply(String text, String stopReason) {
        return new LlmResponse(text, stopReason, 100, 200);
    }

    private static String javaClass(String pkg, String className, boolean withTest) {
        return """
            package %s;

            import org.testng.annotations.Test;

            public class %s {
                %s
                public void works() {}
            }
            """.formatted(pkg, className, withTest ? "@Test" : "");
    }

    private static String fenced(String code) {
        return "```java\n" + code + "```\n";
    }

    private Path projectDir() {
        return tempDir.resolve(PROJECT);
    }

    private List<String> javaFiles() throws IOException {
        if (!Files.exists(projectDir())) return List.of();
        try (Stream<Path> files = Files.list(projectDir())) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    /** Every .java file anywhere under root, as paths relative to root. */
    private static List<String> allJavaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> root.relativize(p).toString())
                    .sorted()
                    .toList();
        }
    }

    private FakeLlmClient generate(LlmResponse response) throws IOException {
        FakeLlmClient client = new FakeLlmClient(response);
        new TestGenerator(client, tempDir).generateFromSpecString(SPEC, PROJECT);
        return client;
    }

    @Test
    @DisplayName("a valid fenced reply writes one file with the right name and package")
    void validReply_writesOneFile() throws IOException {
        generate(reply("Here you go:\n" + fenced(javaClass(PACKAGE, "PetTests", true))));

        assertEquals(List.of("PetTests.java"), javaFiles());
        String written = Files.readString(projectDir().resolve("PetTests.java"));
        assertTrue(written.startsWith("package " + PACKAGE + ";"), written);
        assertTrue(written.contains("public class PetTests"), written);
    }

    @Test
    @DisplayName("a reply with stopReason max_tokens writes no .java file")
    void truncatedReply_writesNoJavaFile() throws IOException {
        generate(reply(fenced(javaClass(PACKAGE, "PetTests", true)), "max_tokens"));

        assertEquals(List.of(), javaFiles());
        assertTrue(Files.exists(projectDir().resolve(TestGenerator.RAW_RESPONSE_FILE)),
                "raw response should be saved for review");
    }

    @Test
    @DisplayName("a block with the wrong package is skipped")
    void wrongPackage_isSkipped() throws IOException {
        generate(reply(fenced(javaClass("com.aitesting.api.petstore.aigenerated", "PetTests", true))));

        assertEquals(List.of(), javaFiles());
    }

    @Test
    @DisplayName("a block with no @Test is skipped")
    void noTestAnnotation_isSkipped() throws IOException {
        generate(reply(fenced(javaClass(PACKAGE, "PetTests", false))));

        assertEquals(List.of(), javaFiles());
    }

    @Test
    @DisplayName("a reply with no code blocks saves llm-raw-response.txt")
    void noCodeBlocks_savesRawResponse() throws IOException {
        String text = "Sorry, I cannot help with that.";
        generate(reply(text));

        Path raw = projectDir().resolve(TestGenerator.RAW_RESPONSE_FILE);
        assertTrue(Files.exists(raw));
        assertEquals(text, Files.readString(raw));
        assertEquals(List.of(), javaFiles());
    }

    @Test
    @DisplayName("the request sent to the client contains the spec text and the expected package")
    void request_containsSpecAndExpectedPackage() throws IOException {
        FakeLlmClient client = generate(reply("no code"));

        assertNotNull(client.lastRequest, "client was not called");
        assertTrue(client.lastRequest.user().contains(SPEC), "spec text missing from prompt");
        assertTrue(client.lastRequest.user().contains("Package: " + PACKAGE),
                "expected package missing from prompt");
        assertEquals(BaseConfig.LLM_MAX_TOKENS, client.lastRequest.maxTokens());
    }

    @Test
    @DisplayName("the prompt contains the framework API summary and the use-only-these-methods rule")
    void request_containsFrameworkApiAndRule() throws IOException {
        FakeLlmClient client = generate(reply("no code"));

        String prompt = client.lastRequest.user();
        assertTrue(prompt.contains(FrameworkApiSummary.build()), "API summary missing from prompt");
        assertTrue(prompt.contains("Use only these methods. Do not invent methods. "
                + "ResponseValidator is fluent: ResponseValidator.from(response).statusCode(200).hasField(...)"),
                "rule missing from prompt");
    }

    @Test
    @DisplayName("a block with no public class name is skipped and no file is written")
    void noPublicClassName_isSkipped() throws IOException {
        String code = javaClass(PACKAGE, "PetTests", true).replace("public class", "class");
        generate(reply(fenced(code)));

        assertEquals(List.of(), allJavaFiles(tempDir));
    }

    @Test
    @DisplayName("a block whose class name is not a valid Java identifier is skipped; nothing is written anywhere")
    void invalidClassName_isSkipped_nothingWrittenOutsideOutputFolder() throws IOException {
        // Output folder is a sub-folder of @TempDir, so a "../../" escape from
        // <output>/petstore/ would land in tempDir itself, where we can see it.
        Path outputBase = tempDir.resolve("out");
        String code = javaClass(PACKAGE, "../../Escaped", true);
        FakeLlmClient client = new FakeLlmClient(reply(fenced(code)));
        new TestGenerator(client, outputBase).generateFromSpecString(SPEC, PROJECT);

        assertEquals(List.of(), allJavaFiles(tempDir));
        assertFalse(Files.exists(tempDir.resolve("Escaped.java")));
        assertFalse(Files.exists(tempDir.getParent().resolve("Escaped.java")));
    }

    @Test
    @DisplayName("two valid blocks write two files")
    void twoValidBlocks_writeTwoFiles() throws IOException {
        generate(reply(fenced(javaClass(PACKAGE, "PetTests", true))
                + "\nand\n"
                + fenced(javaClass(PACKAGE, "StoreTests", true))));

        assertEquals(List.of("PetTests.java", "StoreTests.java"), javaFiles());
    }

    private static String apiClientClass(String className, String extraImport, String body) {
        return """
            package %s;

            import org.testng.annotations.Test;
            import com.aitesting.shared.http.ApiClient;
            %s

            public class %s {
                @Test
                public void works() {
                    %s
                }
            }
            """.formatted(PACKAGE, extraImport, className, body);
    }

    @Test
    @DisplayName("a block using TestNG @Test and ApiClient compiles and is written")
    void compilingBlock_isWritten() throws IOException {
        generate(reply(fenced(apiClientClass("PetTests", "",
                "ApiClient api = ApiClient.DEFAULT; String url = api.getBaseUrl();"))));

        assertEquals(List.of("PetTests.java"), javaFiles());
    }

    @Test
    @DisplayName("a block that imports a class that does not exist is skipped")
    void missingImport_isSkipped() throws IOException {
        generate(reply(fenced(apiClientClass("PetTests",
                "import com.aitesting.shared.http.MagicClient;", "ApiClient api = ApiClient.DEFAULT;"))));

        assertEquals(List.of(), javaFiles());
    }

    @Test
    @DisplayName("a block that calls a method ApiClient does not have is skipped")
    void unknownApiClientMethod_isSkipped() throws IOException {
        generate(reply(fenced(apiClientClass("PetTests", "",
                "ApiClient.DEFAULT.teleport(\"/pet/1\");"))));

        assertEquals(List.of(), javaFiles());
    }

    // ── One class per OpenAPI tag ─────────────────────────────────────────────

    /** Swagger 2.0 spec with three tags; each path string appears only under its own tag. */
    private static final String TAGGED_SPEC = """
        {
          "swagger": "2.0",
          "info": {"title": "Tagged"},
          "basePath": "/v2",
          "tags": [{"name": "pet"}, {"name": "store"}, {"name": "user"}],
          "paths": {
            "/pet": {"post": {"tags": ["pet"], "summary": "Add a pet"}},
            "/pet/{petId}": {"get": {"tags": ["pet"]}, "delete": {"tags": ["pet"]}},
            "/store/order": {"post": {"tags": ["store"]}},
            "/store/inventory": {"get": {"tags": ["store"]}},
            "/user": {"post": {"tags": ["user"]}},
            "/user/login": {"get": {"tags": ["user"]}}
          },
          "definitions": {"Pet": {"type": "object"}}
        }
        """;

    private static final List<String> PET_PATHS = List.of("\"/pet\"", "\"/pet/{petId}\"");
    private static final List<String> STORE_PATHS = List.of("\"/store/order\"", "\"/store/inventory\"");
    private static final List<String> USER_PATHS = List.of("\"/user\"", "\"/user/login\"");

    @Test
    @DisplayName("a spec with three tags sends three requests, each with only its own group's paths")
    void threeTags_threeRequests_eachWithOnlyItsOwnPaths() throws IOException {
        FakeLlmClient client = new FakeLlmClient(reply("no code"));
        new TestGenerator(client, tempDir).generateFromSpecString(TAGGED_SPEC, PROJECT);

        assertEquals(3, client.requests.size());
        List<List<String>> groups = List.of(PET_PATHS, STORE_PATHS, USER_PATHS);
        List<String> classNames = List.of("PetApiGeneratedTests", "StoreApiGeneratedTests", "UserApiGeneratedTests");
        for (int i = 0; i < 3; i++) {
            String prompt = client.requests.get(i).user();
            for (int g = 0; g < 3; g++) {
                for (String path : groups.get(g)) {
                    assertEquals(g == i, prompt.contains(path),
                            "request " + i + (g == i ? " is missing " : " should not contain ") + path);
                }
            }
            assertTrue(prompt.contains("exactly one class, named " + classNames.get(i)),
                    "request " + i + " should ask for " + classNames.get(i));
            assertTrue(prompt.contains("\"definitions\""), "request " + i + " should keep definitions");
        }
    }

    @Test
    @DisplayName("accepted and rejected counts are summed across groups")
    void countsAreCombinedAcrossGroups() throws IOException {
        // pet and store get a valid class; user gets no code block
        FakeLlmClient client = new FakeLlmClient(request -> {
            String prompt = request.user();
            if (prompt.contains("named PetApiGeneratedTests")) {
                return reply(fenced(javaClass(PACKAGE, "PetApiGeneratedTests", true)));
            }
            if (prompt.contains("named StoreApiGeneratedTests")) {
                return reply(fenced(javaClass(PACKAGE, "StoreApiGeneratedTests", true)));
            }
            return reply("Sorry, no code.");
        });

        GenerationResult result = new TestGenerator(client, tempDir).generateFromSpecString(TAGGED_SPEC, PROJECT);

        assertEquals(new GenerationResult(2, 1), result);
        assertEquals(List.of("PetApiGeneratedTests.java", "StoreApiGeneratedTests.java"), javaFiles());
        assertTrue(Files.exists(projectDir().resolve("llm-raw-response-user.txt")));
    }

    // ── Max tokens and tests-per-class cap ────────────────────────────────────

    private TestGenerator generatorWith(FakeLlmClient client, int maxTokens, int maxTestsPerClass) {
        return new TestGenerator(client, tempDir, new JavaCompileCheck(), maxTokens, maxTestsPerClass);
    }

    @Test
    @DisplayName("the request carries the configured max tokens")
    void request_carriesConfiguredMaxTokens() throws IOException {
        FakeLlmClient client = new FakeLlmClient(reply("no code"));
        generatorWith(client, 12_345, 8).generateFromSpecString(TAGGED_SPEC, PROJECT);

        assertEquals(3, client.requests.size());
        for (LlmRequest request : client.requests) {
            assertEquals(12_345, request.maxTokens());
        }
    }

    @Test
    @DisplayName("the prompt contains the tests-per-class cap rule with the configured number")
    void prompt_containsCapNumber() throws IOException {
        FakeLlmClient client = new FakeLlmClient(reply("no code"));
        generatorWith(client, 16_000, 5).generateFromSpecString(SPEC, PROJECT);

        String prompt = client.lastRequest.user();
        assertTrue(prompt.contains("Write at most 5 @Test methods. Choose the most valuable: "
                + "one happy path per endpoint first, then the most important negative cases "
                + "(not found, invalid input). Do not write near-duplicate tests."), prompt);
    }

    private static String classWithTests(String className, int tests) {
        StringBuilder methods = new StringBuilder();
        for (int i = 0; i < tests; i++) {
            methods.append("    @Test\n    public void t").append(i).append("() {}\n");
        }
        return """
            package %s;

            import org.testng.annotations.Test;

            public class %s {
            %s}
            """.formatted(PACKAGE, className, methods);
    }

    @Test
    @DisplayName("a class over the cap is still written and logs a WARN with the count")
    void overCap_isWritten_andLogsWarn() throws IOException {
        Logger generatorLog = (Logger) LoggerFactory.getLogger(TestGenerator.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        generatorLog.addAppender(logs);
        try {
            FakeLlmClient client = new FakeLlmClient(reply(fenced(classWithTests("PetTests", 4))));
            GenerationResult result = generatorWith(client, 16_000, 3).generateFromSpecString(SPEC, PROJECT);

            assertEquals(new GenerationResult(1, 0), result);
            assertEquals(List.of("PetTests.java"), javaFiles());
            List<String> warns = logs.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN)
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
            assertEquals(List.of("PetTests has 4 @Test methods, more than the cap of 3 "
                    + "(LLM_MAX_TESTS_PER_CLASS); written anyway"), warns);
        } finally {
            generatorLog.detachAppender(logs);
        }
    }

    @Test
    @DisplayName("a class at the cap logs no WARN")
    void atCap_logsNoWarn() throws IOException {
        Logger generatorLog = (Logger) LoggerFactory.getLogger(TestGenerator.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        generatorLog.addAppender(logs);
        try {
            FakeLlmClient client = new FakeLlmClient(reply(fenced(classWithTests("PetTests", 3))));
            generatorWith(client, 16_000, 3).generateFromSpecString(SPEC, PROJECT);

            assertEquals(List.of("PetTests.java"), javaFiles());
            assertTrue(logs.list.stream().noneMatch(e -> e.getLevel() == Level.WARN));
        } finally {
            generatorLog.detachAppender(logs);
        }
    }
}
