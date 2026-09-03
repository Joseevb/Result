package io.github.joseevb.result.examples;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.joseevb.result.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class ResultExamples {

  private ResultExamples() {}

  public static void main(String... args) throws IOException {

    final Options options;

    try {
      options = Options.parse(args);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      usage();
      return;
    }

    final var examples = examples();

    if (options.example().equals("help")) {
      usage();
      return;
    }

    if (options.example().equals("list")) {
      listExamples(examples);
      return;
    }

    final List<Example> selected =
        options.example().equals("all")
            ? examples
            : examples.stream()
                .filter(example -> example.name().equals(options.example()))
                .toList();

    if (selected.isEmpty()) {
      System.err.println("Unknown example: " + options.example());
      listExamples(examples);
      return;
    }

    IO.println("Result examples (implementation: " + options.implementation() + ")");

    try (var fixture = Fixture.create()) {
      for (var example : selected) {
        runExample(example, fixture, options);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Examples
  // ---------------------------------------------------------------------------

  private static List<Example> examples() {
    return List.of(
        new Example(
            "simple",
            "Read a file: checked exception versus Result.from().",
            ResultExamples::simpleVanilla,
            ResultExamples::simpleResult),
        new Example(
            "pipeline",
            "Read -> parse -> validate -> transform.",
            fixture -> pipelineVanilla(fixture, "port.txt"),
            fixture -> pipelineResult(fixture, "port.txt")),
        new Example(
            "pipeline-error",
            "Same pipeline, but parsing fails.",
            fixture -> pipelineVanilla(fixture, "bad-port.txt"),
            fixture -> pipelineResult(fixture, "bad-port.txt")),
        new Example(
            "pipeline-validation",
            "Same pipeline, but validation rejects an out-of-range port.",
            fixture -> pipelineVanilla(fixture, "invalid-port.txt"),
            fixture -> pipelineResult(fixture, "invalid-port.txt")),
        new Example(
            "recovery",
            "Try a primary file, then fall back to another file.",
            ResultExamples::recoveryVanilla,
            ResultExamples::recoveryResult),
        new Example(
            "combine",
            "Combine two independent fallible values into one configuration.",
            ResultExamples::combineVanilla,
            ResultExamples::combineResult),
        new Example(
            "bulk",
            "Read several files, stopping on the first failure.",
            fixture -> bulkVanilla(fixture, false),
            fixture -> bulkResult(fixture, false)),
        new Example(
            "bulk-error",
            "Bulk processing where one of the files does not exist.",
            fixture -> bulkVanilla(fixture, true),
            fixture -> bulkResult(fixture, true)),
        new Example(
            "workflow",
            "A larger workflow combining I/O, parsing, validation and bulk processing.",
            ResultExamples::workflowVanilla,
            ResultExamples::workflowResult));
  }

  // ---------------------------------------------------------------------------
  // 1. Simple
  // ---------------------------------------------------------------------------

  private static Outcome simpleVanilla(Fixture fixture) {

    final String content;

    try {
      content = Files.readString(fixture.path("hello.txt"), UTF_8);
    } catch (IOException e) {
      return Outcome.error(e);
    }

    return Outcome.ok(content.strip(), content.length());
  }

  private static Outcome simpleResult(Fixture fixture) {

    final var result = Result.from(() -> Files.readString(fixture.path("hello.txt"), UTF_8));

    return result.fold(content -> Outcome.ok(content.strip(), content.length()), Outcome::error);
  }

  // ---------------------------------------------------------------------------
  // 2. Pipeline
  // ---------------------------------------------------------------------------

  private static Outcome pipelineVanilla(Fixture fixture, String file) {

    final String raw;

    try {
      raw = Files.readString(fixture.path(file), UTF_8);
    } catch (IOException e) {
      return Outcome.error(readFailure(fixture.path(file), e));
    }

    final int port;

    try {
      port = Integer.parseInt(raw.strip());
    } catch (NumberFormatException _) {
      return Outcome.error(new ParseFailure("port", raw.strip()));
    }

    if (port < 1 || port > 65_535) {
      return Outcome.error(new ValidationFailure("port", "must be between 1 and 65535"));
    }

    final var endpoint = new Endpoint("localhost", port);

    return Outcome.ok(endpoint.toString(), 1);
  }

  private static Outcome pipelineResult(Fixture fixture, String file) {

    final var result =
        readText(fixture.path(file))
            .andThen(raw -> parseInteger(raw, "port"))
            .andThen(ResultExamples::validatePort)
            .map(port -> new Endpoint("localhost", port));

    return result.fold(endpoint -> Outcome.ok(endpoint.toString(), 1), Outcome::error);
  }

  // ---------------------------------------------------------------------------
  // 3. Recovery
  // ---------------------------------------------------------------------------

  private static Outcome recoveryVanilla(Fixture fixture) {
    try {
      final var content = Files.readString(fixture.path("primary.txt"), UTF_8);

      return Outcome.ok(content.strip(), content.length());

    } catch (IOException _) {

      try {
        final var content = Files.readString(fixture.path("fallback.txt"), UTF_8);

        return Outcome.ok(content.strip(), content.length());

      } catch (IOException fallbackError) {
        return Outcome.error(readFailure(fixture.path("fallback.txt"), fallbackError));
      }
    }
  }

  private static Outcome recoveryResult(Fixture fixture) {

    final var result =
        readText(fixture.path("primary.txt"))
            .recoverWith(error -> readText(fixture.path("fallback.txt")));

    return result.fold(content -> Outcome.ok(content.strip(), content.length()), Outcome::error);
  }

  // ---------------------------------------------------------------------------
  // 4. Combine
  // ---------------------------------------------------------------------------

  private static Outcome combineVanilla(Fixture fixture) {

    final String username;

    try {
      username = Files.readString(fixture.path("username.txt"), UTF_8).strip();
    } catch (IOException e) {
      return Outcome.error(readFailure(fixture.path("username.txt"), e));
    }

    final String rawPort;

    try {
      rawPort = Files.readString(fixture.path("port.txt"), UTF_8);
    } catch (IOException e) {
      return Outcome.error(readFailure(fixture.path("port.txt"), e));
    }

    final int port;

    try {
      port = Integer.parseInt(rawPort.strip());
    } catch (NumberFormatException _) {
      return Outcome.error(new ParseFailure("port", rawPort.strip()));
    }

    if (port < 1 || port > 65_535) {
      return Outcome.error(new ValidationFailure("port", "must be between 1 and 65535"));
    }

    final var config = new ServiceConfig(username, port);

    return Outcome.ok(config.toString(), 2);
  }

  private static Outcome combineResult(Fixture fixture) {

    final var username = readText(fixture.path("username.txt")).map(String::strip);

    final var port =
        readText(fixture.path("port.txt"))
            .andThen(raw -> parseInteger(raw, "port"))
            .andThen(ResultExamples::validatePort);

    final var config = username.combine(port, ServiceConfig::new);

    return config.fold(value -> Outcome.ok(value.toString(), 2), Outcome::error);
  }

  // ---------------------------------------------------------------------------
  // 5. Bulk
  // ---------------------------------------------------------------------------

  private static Outcome bulkVanilla(Fixture fixture, boolean includeMissingFile) {

    final var contents = new ArrayList<String>();

    for (var path : fixture.partFiles(includeMissingFile)) {
      try {
        contents.add(Files.readString(path, UTF_8));
      } catch (IOException e) {
        return Outcome.error(readFailure(path, e));
      }
    }

    final var summary = summarize(contents);

    return Outcome.ok(summary.toString(), summary.characters());
  }

  private static Outcome bulkResult(Fixture fixture, boolean includeMissingFile) {

    final var result =
        Result.collect(fixture.partFiles(includeMissingFile).stream().map(ResultExamples::readText))
            .map(ResultExamples::summarize);

    return result.fold(
        summary -> Outcome.ok(summary.toString(), summary.characters()), Outcome::error);
  }

  // ---------------------------------------------------------------------------
  // 6. Larger workflow
  // ---------------------------------------------------------------------------

  private static Outcome workflowVanilla(Fixture fixture) {

    // Read username

    final String username;

    try {
      username = Files.readString(fixture.path("username.txt"), UTF_8).strip();
    } catch (IOException e) {
      return Outcome.error(readFailure(fixture.path("username.txt"), e));
    }

    // Read port

    final String rawPort;

    try {
      rawPort = Files.readString(fixture.path("port.txt"), UTF_8);
    } catch (IOException e) {
      return Outcome.error(readFailure(fixture.path("port.txt"), e));
    }

    // Parse port

    final int port;

    try {
      port = Integer.parseInt(rawPort.strip());
    } catch (NumberFormatException _) {
      return Outcome.error(new ParseFailure("port", rawPort.strip()));
    }

    // Validate port

    if (port < 1 || port > 65_535) {
      return Outcome.error(new ValidationFailure("port", "must be between 1 and 65535"));
    }

    final var config = new ServiceConfig(username, port);

    // Read input files

    final var contents = new ArrayList<String>();

    for (var path : fixture.partFiles(false)) {
      try {
        contents.add(Files.readString(path, UTF_8));
      } catch (IOException e) {
        return Outcome.error(readFailure(path, e));
      }
    }

    // Produce report

    final var batch = summarize(contents);

    final var report = new Report(config, batch.files(), batch.lines(), batch.characters());

    return Outcome.ok(report.toString(), report.characters());
  }

  private static Outcome workflowResult(Fixture fixture) {

    final Result<Report, DemoError> result =
        readText(fixture.path("username.txt"))
            .map(String::strip)
            .andThen(
                username ->
                    readText(fixture.path("port.txt"))
                        .andThen(raw -> parseInteger(raw, "port"))
                        .andThen(ResultExamples::validatePort)
                        .map(port -> new ServiceConfig(username, port)))
            .andThen(
                config ->
                    Result.collect(fixture.partFiles(false).stream().map(ResultExamples::readText))
                        .map(ResultExamples::summarize)
                        .map(
                            batch ->
                                new Report(
                                    config, batch.files(), batch.lines(), batch.characters())));

    return result.fold(
        report -> Outcome.ok(report.toString(), report.characters()), Outcome::error);
  }

  // ---------------------------------------------------------------------------
  // Result helpers
  // ---------------------------------------------------------------------------

  private static Result<String, DemoError> readText(Path path) {

    return Result.from(
        () -> Files.readString(path, UTF_8), exception -> readFailure(path, exception));
  }

  private static Result<Integer, DemoError> parseInteger(String raw, String field) {

    final var text = raw.strip();

    try {
      return Result.ok(Integer.parseInt(text));
    } catch (NumberFormatException _) {
      return Result.err(new ParseFailure(field, text));
    }
  }

  private static Result<Integer, DemoError> validatePort(int port) {

    if (port < 1 || port > 65_535) {
      return Result.err(new ValidationFailure("port", "must be between 1 and 65535"));
    }

    return Result.ok(port);
  }

  private static ReadFailure readFailure(Path path, Exception exception) {

    return new ReadFailure(path.getFileName().toString(), exception.getClass().getSimpleName());
  }

  private static BatchSummary summarize(List<String> contents) {

    final int characters = contents.stream().mapToInt(String::length).sum();

    final int lines = contents.stream().mapToInt(content -> (int) content.lines().count()).sum();

    return new BatchSummary(contents.size(), lines, characters);
  }

  // ---------------------------------------------------------------------------
  // Harness
  // ---------------------------------------------------------------------------

  private static void runExample(Example example, Fixture fixture, Options options) {

    IO.println();
    IO.println("=== " + example.name() + " ===");
    IO.println(example.description());

    Outcome vanillaSample = null;
    Outcome resultSample = null;

    if (options.implementation() != Implementation.RESULT) {
      vanillaSample = example.vanilla().run(fixture);
      IO.println("vanilla sample: " + vanillaSample.display());
    }

    if (options.implementation() != Implementation.VANILLA) {
      resultSample = example.result().run(fixture);
      IO.println("result  sample: " + resultSample.display());
    }

    if (vanillaSample != null && resultSample != null && !vanillaSample.equals(resultSample)) {

      System.err.println("WARNING: implementations produced different outcomes");
    }
  }

  private static void listExamples(List<Example> examples) {

    IO.println("Available examples:");
    IO.println();

    for (var example : examples) {
      IO.println("  %-16s %s".formatted(example.name(), example.description()));
    }

    IO.println();
    IO.println("  all              Run every example");
  }

  private static void usage() {

    IO.println(
        """
        Usage:

          ResultExamples <example> [options]

        Examples:

          ResultExamples list
          ResultExamples simple
          ResultExamples pipeline
          ResultExamples pipeline-error
          ResultExamples pipeline-validation
          ResultExamples recovery
          ResultExamples combine
          ResultExamples bulk
          ResultExamples bulk-error
          ResultExamples workflow
          ResultExamples all

        Options:

          --impl=both|vanilla|result
              Which implementation to execute.
              Default: both

        Examples:

          ResultExamples pipeline --impl=result
        """);
  }

  // ---------------------------------------------------------------------------
  // Harness types
  // ---------------------------------------------------------------------------

  @FunctionalInterface
  private interface Scenario {
    Outcome run(Fixture fixture);
  }

  private record Example(String name, String description, Scenario vanilla, Scenario result) {}

  private record Outcome(boolean ok, String value, long payload) {

    static Outcome ok(Object value, long payload) {
      return new Outcome(true, String.valueOf(value), payload);
    }

    static Outcome error(Exception exception) {
      final var message = exception.getMessage();

      return new Outcome(
          false, exception.getClass().getSimpleName() + (message == null ? "" : ": " + message), 0);
    }

    static Outcome error(DemoError error) {
      return new Outcome(false, error.message(), 0);
    }

    String display() {
      return ok ? "Ok[" + value + "]" : "Err[" + value + "]";
    }
  }

  private enum Implementation {
    VANILLA,
    RESULT,
    BOTH;

    static Implementation parse(String value) {
      return switch (value.toLowerCase(Locale.ROOT)) {
        case "vanilla" -> VANILLA;
        case "result" -> RESULT;
        case "both" -> BOTH;
        default -> throw new IllegalArgumentException("Unknown implementation: " + value);
      };
    }
  }

  private record Options(String example, Implementation implementation) {

    static Options parse(String[] args) {

      String example = "list";
      Implementation implementation = Implementation.BOTH;

      boolean exampleSpecified = false;

      for (var arg : args) {

        if (arg.equals("-h") || arg.equals("--help")) {
          example = "help";
          continue;
        }

        if (arg.startsWith("--impl=")) {
          implementation = Implementation.parse(arg.substring("--impl=".length()));
          continue;
        }

        if (arg.startsWith("--")) {
          throw new IllegalArgumentException("Unknown option: " + arg);
        }

        if (exampleSpecified) {
          throw new IllegalArgumentException("Only one example name may be specified");
        }

        example = arg;
        exampleSpecified = true;
      }

      return new Options(example, implementation);
    }
  }

  // ---------------------------------------------------------------------------
  // Domain model
  // ---------------------------------------------------------------------------

  private record Endpoint(String host, int port) {}

  private record ServiceConfig(String username, int port) {}

  private record BatchSummary(int files, int lines, int characters) {}

  private record Report(ServiceConfig config, int files, int lines, int characters) {}

  // ---------------------------------------------------------------------------
  // Domain errors
  // ---------------------------------------------------------------------------

  private sealed interface DemoError permits ReadFailure, ParseFailure, ValidationFailure {

    String message();
  }

  private record ReadFailure(String file, String cause) implements DemoError {

    @Override
    public String message() {
      return "Could not read '%s': %s".formatted(file, cause);
    }
  }

  private record ParseFailure(String field, String value) implements DemoError {

    @Override
    public String message() {
      return "Could not parse %s from '%s'".formatted(field, value);
    }
  }

  private record ValidationFailure(String field, String reason) implements DemoError {

    @Override
    public String message() {
      return "%s %s".formatted(field, reason);
    }
  }

  // ---------------------------------------------------------------------------
  // Test fixture
  // ---------------------------------------------------------------------------

  private record Fixture(Path root) implements AutoCloseable {

    static Fixture create() throws IOException {

      final var root = Files.createTempDirectory("result-examples-");

      Files.writeString(root.resolve("hello.txt"), "Hello from Result\n", UTF_8);

      Files.writeString(root.resolve("username.txt"), "jose\n", UTF_8);

      Files.writeString(root.resolve("port.txt"), "8080\n", UTF_8);

      Files.writeString(root.resolve("bad-port.txt"), "not-a-number\n", UTF_8);

      Files.writeString(root.resolve("invalid-port.txt"), "70000\n", UTF_8);

      // primary.txt is intentionally NOT created.

      Files.writeString(root.resolve("fallback.txt"), "Loaded from fallback\n", UTF_8);

      final var parts = Files.createDirectories(root.resolve("parts"));

      Files.writeString(
          parts.resolve("one.txt"),
          """
              alpha
              beta
              """,
          UTF_8);

      Files.writeString(
          parts.resolve("two.txt"),
          """
              gamma
              delta
              """,
          UTF_8);

      Files.writeString(
          parts.resolve("three.txt"),
          """
              epsilon
              zeta
              eta
              """,
          UTF_8);

      return new Fixture(root);
    }

    Path path(String name) {
      return root.resolve(name);
    }

    List<Path> partFiles(boolean includeMissingFile) {

      if (includeMissingFile) {
        return List.of(
            path("parts/one.txt"),
            path("parts/missing.txt"),
            path("parts/two.txt"),
            path("parts/three.txt"));
      }

      return List.of(path("parts/one.txt"), path("parts/two.txt"), path("parts/three.txt"));
    }

    @Override
    public void close() throws IOException {

      try (var paths = Files.walk(root)) {
        for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {

          Files.deleteIfExists(path);
        }
      }
    }
  }
}
