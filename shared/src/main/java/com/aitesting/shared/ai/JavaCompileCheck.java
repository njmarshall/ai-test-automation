package com.aitesting.shared.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Compiles one generated Java source string in memory and returns the
 * compiler errors. An empty list means the source compiles.
 *
 * Class files go to a temp folder that is deleted afterwards. The classpath
 * comes from, in order: the {@code surefire.test.class.path} system property,
 * the URLs of the thread context class loader (when it is a URLClassLoader,
 * as under {@code mvn exec:java}), or {@code java.class.path}.
 *
 * If no system compiler exists (running on a JRE), the check is skipped with
 * one WARN log and every source is treated as compiling.
 */
public class JavaCompileCheck {

    private static final Logger log = LoggerFactory.getLogger(JavaCompileCheck.class);

    private final AtomicBoolean warnedNoCompiler = new AtomicBoolean();

    /**
     * @param className simple name of the public class (the in-memory file is named after it)
     * @param source    full Java source
     * @return error diagnostics; empty if the source compiles (or no compiler is available)
     */
    public List<Diagnostic<? extends JavaFileObject>> errors(String className, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            if (warnedNoCompiler.compareAndSet(false, true)) {
                log.warn("No system Java compiler (running on a JRE?); compile check of generated tests is skipped");
            }
            return List.of();
        }

        Path outDir;
        try {
            outDir = Files.createTempDirectory("ai-compile-check");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            List<String> options = List.of(
                    "-classpath", classpath(),
                    "-d", outDir.toString(),
                    "-proc:none",
                    "-nowarn",
                    "-encoding", "UTF-8");
            try (StandardJavaFileManager fileManager =
                         compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
                compiler.getTask(null, fileManager, diagnostics, options, null,
                        List.of(new SourceString(className, source))).call();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            List<Diagnostic<? extends JavaFileObject>> errors = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) errors.add(d);
            }
            return errors;
        } finally {
            deleteRecursively(outDir);
        }
    }

    /** Surefire and exec:java both hide the real project classpath from java.class.path. */
    static String classpath() {
        String surefire = System.getProperty("surefire.test.class.path");
        if (surefire != null && !surefire.isBlank()) {
            log.debug("Compile check classpath from surefire.test.class.path");
            return surefire;
        }
        if (Thread.currentThread().getContextClassLoader() instanceof URLClassLoader loader) {
            List<String> entries = new ArrayList<>();
            for (URL url : loader.getURLs()) {
                try {
                    entries.add(Paths.get(url.toURI()).toString());
                } catch (URISyntaxException | IllegalArgumentException e) {
                    log.debug("Compile check classpath: ignoring non-file URL {}", url);
                }
            }
            log.debug("Compile check classpath from context URLClassLoader ({} entries)", entries.size());
            return String.join(File.pathSeparator, entries);
        }
        log.debug("Compile check classpath from java.class.path");
        return System.getProperty("java.class.path");
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("Could not delete {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.debug("Could not clean up {}: {}", dir, e.getMessage());
        }
    }

    /** A Java source file held in memory. */
    private static final class SourceString extends SimpleJavaFileObject {
        private final String source;

        SourceString(String className, String source) {
            super(URI.create("string:///" + className + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
