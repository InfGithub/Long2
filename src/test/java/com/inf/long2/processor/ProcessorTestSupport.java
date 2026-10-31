package com.inf.long2.processor;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.io.TempDir;

abstract class ProcessorTestSupport {
    private static final String IMPORTS = """
            package fixture;
            import com.inf.long2.util.annotations.StaticFieldImplementsCheck;
            import com.inf.long2.util.annotations.StaticMethodImplementsCheck;
            import com.inf.long2.util.annotations.HaveToImplements;
            import com.inf.long2.util.annotations.KeepContractType;
            """;

    @TempDir
    Path temporaryDirectory;

    final void assertCompiles(String source) throws IOException {
        Compilation result = compile(source);
        assertTrue(result.success(), result.messages());
    }

    final Compilation compile(String source) throws IOException {
        return compile(source, true, null);
    }

    final Compilation compile(String source, boolean process, Path dependency) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        Path output = Files.createTempDirectory(temporaryDirectory, "classes-");
        String classpath = System.getProperty("java.class.path");
        if (dependency != null) {
            classpath += File.pathSeparator + dependency;
        }

        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            manager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(output));
            List<String> options = new ArrayList<>(List.of("-classpath", classpath));
            if (!process) {
                options.add("-proc:none");
            }
            var unit = new SimpleJavaFileObject(URI.create("string:///fixture/Fixture.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return IMPORTS + source;
                }
            };
            var task = compiler.getTask(null, manager, diagnostics, options, null, List.of(unit));
            if (process) {
                task.setProcessors(List.of(new StaticMethodImplementsCheckProcessor()));
            }
            boolean success = task.call();
            return new Compilation(success, List.copyOf(diagnostics.getDiagnostics()), output);
        }
    }

    final Path createJar(Path classes) throws IOException {
        Path jar = Files.createTempFile(temporaryDirectory, "contracts-", ".jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar));
                var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    record Compilation(
            boolean success,
            List<Diagnostic<? extends JavaFileObject>> diagnostics,
            Path output) {
        String messages() {
            return diagnostics.toString();
        }
    }
}
