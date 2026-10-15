package io.github.joseevb.result.gen;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.joseevb.result.Result;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultGenTransformerTest {
  @TempDir Path tempDir;

  @Test
  void generatedSourceCompilesWithJavacAndContainsHeterogeneousUnion()
      throws IOException, URISyntaxException {
    String source = """
        package prototype;

        import io.github.joseevb.result.Result;

        class Fixture {
          record User(String id) {}
          record Product(String name) {}
          record Cart(User user, Product product) {}
          record UserError(String message) {}
          record ProductError(String message) {}

          static Result<User, UserError> getUser() {
            return Result.ok(new User("u-1"));
          }

          static Result<Product, ProductError> getProduct(String userId) {
            return Result.ok(new Product(userId));
          }

          static Result<Cart, ?> run() {
            return Result.gen($ -> {
              var user = $.bind(getUser());
              var product = $.bind(getProduct(user.id()));
              return new Cart(user, product);
            });
          }

          static String message(Result<Cart, ?> result) {
            if (!(result instanceof Result.Err<Cart, ?> err)) {
              throw new IllegalArgumentException("Expected Err");
            }
            return switch (err.error()) {
              case UserError error -> error.message();
              case ProductError error -> error.message();
            };
          }
        }
        """;

    Set<String> entries = new LinkedHashSet<>();
    String currentClasspath = System.getProperty("java.class.path");
    if (currentClasspath != null && !currentClasspath.isBlank()) {
      entries.addAll(java.util.List.of(currentClasspath.split(java.io.File.pathSeparator)));
    }
    entries.add(
        Path.of(Result.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
    entries.add(
        Path.of(
                ResultGenTransformer.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .toString());
    String classpath = String.join(java.io.File.pathSeparator, entries);

    String transformed =
        ResultGenTransformer.transform(
            source, "Fixture.java", entries.toArray(String[]::new));

    assertTrue(transformed.contains("sealed interface ResultGenErrors_"));
    assertTrue(transformed.contains("record Bind0(prototype.Fixture.UserError value)"));
    assertTrue(transformed.contains("record Bind1(prototype.Fixture.ProductError value)"));
    assertTrue(transformed.contains(".mapErr(error -> new ResultGenErrors_"));
    assertTrue(transformed.contains("Result.gen($ ->"));
    assertTrue(transformed.contains(".Bind0(var error) ->"));
    assertTrue(transformed.contains(".Bind1(var error) ->"));

    String nonExhaustive = source.replace(
        "              case ProductError error -> error.message();\n", "");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ResultGenTransformer.transform(
                nonExhaustive, "Fixture.java", entries.toArray(String[]::new)));

    String withDefault =
        source.replace(
            "              case ProductError error -> error.message();\n",
            "              case ProductError error -> error.message();\n"
                + "              default -> throw new AssertionError();\n");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ResultGenTransformer.transform(
                withDefault, "Fixture.java", entries.toArray(String[]::new)));

    String withUnrelatedType =
        source.replace(
            "              case ProductError error -> error.message();\n",
            "              case ProductError error -> error.message();\n"
                + "              case Integer i -> \"Integer error: \" + i;\n");
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ResultGenTransformer.transform(
                withUnrelatedType, "Fixture.java", entries.toArray(String[]::new)));

    Path sourceFile = tempDir.resolve("Fixture.java");
    Path outputDir = tempDir.resolve("classes");
    Files.createDirectories(outputDir);
    Files.writeString(sourceFile, transformed, StandardCharsets.UTF_8);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null, "Tests require a JDK, not a JRE");
    int exitCode =
        compiler.run(
            null,
            null,
            null,
            "--release",
            "25",
            "-classpath",
            classpath,
            "-d",
            outputDir.toString(),
            sourceFile.toString());

    assertTrue(exitCode == 0, "javac rejected transformed source");
  }
}
