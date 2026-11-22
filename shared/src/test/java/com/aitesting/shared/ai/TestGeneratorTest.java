package com.aitesting.shared.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    /** Returns a scripted reply and records the request it received. */
    static final class FakeLlmClient implements LlmClient {
        private final LlmResponse reply;
        LlmRequest lastRequest;

        FakeLlmClient(LlmResponse reply) {
            this.reply = reply;
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            this.lastRequest = request;
            return reply;
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
        assertEquals(TestGenerator.MAX_TOKENS, client.lastRequest.maxTokens());
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
}
