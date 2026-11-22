package com.aitesting.shared.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestGeneratorRunnerTest — exit codes of {@link TestGeneratorRunner#run}.
 * The spec is a local temp file and the model is a FakeLlmClient, so no
 * network call is made.
 */
@DisplayName("TestGeneratorRunner")
class TestGeneratorRunnerTest {

    private static final String PACKAGE = "com.aitesting.api.aigenerated.petstore";

    @TempDir
    Path tempDir;

    private final Logger runnerLog = (Logger) LoggerFactory.getLogger(TestGeneratorRunner.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        runnerLog.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        runnerLog.detachAppender(logs);
    }

    private String[] args() throws IOException {
        Path spec = tempDir.resolve("spec.json");
        Files.writeString(spec, "{\"swagger\":\"2.0\",\"info\":{\"title\":\"Runner test\"}}");
        return new String[] {spec.toString(), "petstore"};
    }

    private int run(String replyText) throws IOException {
        TestGeneratorTest.FakeLlmClient client =
                new TestGeneratorTest.FakeLlmClient(new LlmResponse(replyText, "end_turn", 1, 1));
        Path out = tempDir.resolve("out");
        return TestGeneratorRunner.run(args(), () -> new TestGenerator(client, out));
    }

    private boolean logged(String message) {
        return logs.list.stream().anyMatch(e -> e.getFormattedMessage().contains(message));
    }

    @Test
    @DisplayName("exits 3 and logs 'No generated tests were accepted' when nothing is accepted")
    void noneAccepted_exits3() throws IOException {
        int code = run("Sorry, no code today.");

        assertEquals(3, code);
        assertTrue(logged("No generated tests were accepted"), "missing log line");
    }

    @Test
    @DisplayName("exits 0 when at least one test is accepted")
    void oneAccepted_exits0() throws IOException {
        String code = """
            package %s;

            import org.testng.annotations.Test;

            public class PetApiGeneratedTests {
                @Test
                public void works() {}
            }
            """.formatted(PACKAGE);

        assertEquals(0, run("```java\n" + code + "```\n"));
        assertFalse(logged("No generated tests were accepted"));
    }

    @Test
    @DisplayName("exits 2 on a configuration error such as a missing API key")
    void configurationError_exits2() throws IOException {
        int code = TestGeneratorRunner.run(args(), () -> {
            throw new IllegalStateException("LLM_API_KEY is not set");
        });

        assertEquals(2, code);
    }

    @Test
    @DisplayName("exits 1 when the spec cannot be found")
    void missingSpec_exits1() {
        String missing = tempDir.resolve("no-such-spec.json").toString();

        assertEquals(1, TestGeneratorRunner.run(new String[] {missing, "petstore"},
                () -> fail("generator should not be created")));
    }
}
