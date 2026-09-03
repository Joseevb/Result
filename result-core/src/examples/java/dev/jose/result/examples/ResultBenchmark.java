package dev.jose.result.examples;

import dev.jose.result.Result;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;

/**
 * Measures error-handling overhead for an in-memory parse, validate, and domain-model pipeline.
 *
 * <p>Each benchmark operation processes a batch of 256 inputs. The {@code success} input contains
 * varied valid ports. Other inputs contain approximately 1%, 10%, or 100% failures split between
 * parse and validation errors. Result values are consumed in the terminal stream stage so they
 * remain eligible for escape analysis.
 *
 * <p>The optimized implementation is intentionally not idiomatic. It avoids intermediate objects,
 * exceptions, stripped strings, and general-purpose parsing to establish a practical lower bound.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
@Threads(1)
@State(Scope.Benchmark)
public class ResultBenchmark {

  private static final int BATCH_SIZE = 256;
  private static final int PARSE_FAILED = Integer.MIN_VALUE;
  private static final long PARSE_ERROR_CHECKSUM = -1;
  private static final long VALIDATION_ERROR_CHECKSUM = -2;

  private static final String USERNAME = "jose";
  private final String[] ports = new String[BATCH_SIZE];

  @Param({"success", "one-percent", "ten-percent", "failure"})
  private String input;

  @org.openjdk.jmh.annotations.Setup
  public void setUp() {
    for (int i = 0; i < ports.length; i++) {
      final String validPort = " " + (8_000 + i % 1_000) + " ";
      ports[i] =
          switch (input) {
            case "success" -> validPort;
            case "one-percent" -> i % 100 == 50 ? failureValue(i / 100) : validPort;
            case "ten-percent" -> i % 10 == 5 ? failureValue(i / 10) : validPort;
            case "failure" -> failureValue(i);
            default -> throw new IllegalStateException("Unknown input: " + input);
          };
    }

    final long expected = heavilyOptimizedJava();
    if (vanillaJava() != expected
        || result() != expected
        || vanillaJavaWithoutExceptions() != expected
        || resultWithoutExceptions() != expected) {
      throw new IllegalStateException("Benchmark implementations produced different results");
    }

    if (vanillaBatchWithoutExceptions() != resultBatchWithoutExceptions()) {
      throw new IllegalStateException("Batch implementations produced different results");
    }
  }

  @Benchmark
  public long vanillaJava() {
    return Arrays.stream(ports).mapToLong(this::processVanillaPort).sum();
  }

  @Benchmark
  public long result() {
    return Arrays.stream(ports)
        .mapToLong(
            rawPort -> processWithResult(rawPort).fold(Endpoint::checksum, PortError::checksum))
        .sum();
  }

  @Benchmark
  public long vanillaJavaWithoutExceptions() {
    return Arrays.stream(ports).mapToLong(this::processVanillaPortWithoutExceptions).sum();
  }

  @Benchmark
  public long resultWithoutExceptions() {
    return Arrays.stream(ports)
        .mapToLong(
            rawPort ->
                processWithResultWithoutExceptions(rawPort)
                    .fold(Endpoint::checksum, PortError::checksum))
        .sum();
  }

  @Benchmark
  public long vanillaBatchWithoutExceptions() {
    return processVanillaBatch();
  }

  @Benchmark
  public long resultBatchWithoutExceptions() {
    return processResultBatch().fold(BatchReport::checksum, PortError::checksum);
  }

  @Benchmark
  public long heavilyOptimizedJava() {
    long checksum = 0;
    final int usernameHash = USERNAME.hashCode();
    final String[] values = ports;

    for (int index = 0, length = values.length; index < length; index++) {
      final String value = values[index];
      final int valueLength = value.length();
      int cursor = 0;

      while (cursor < valueLength && value.charAt(cursor) <= ' ') {
        cursor++;
      }

      int port = 0;
      int digits = 0;
      while (cursor < valueLength) {
        final int digit = value.charAt(cursor) - '0';
        if (digit < 0 || digit > 9) {
          break;
        }
        port = port * 10 + digit;
        digits++;
        cursor++;
      }

      while (cursor < valueLength && value.charAt(cursor) <= ' ') {
        cursor++;
      }

      if (digits == 0 || cursor != valueLength) {
        checksum += PARSE_ERROR_CHECKSUM;
      } else if (port < 1 || port > 65_535) {
        checksum += VALIDATION_ERROR_CHECKSUM;
      } else {
        checksum += 31L * usernameHash + port;
      }
    }

    return checksum;
  }

  public static void main(String... args) throws Exception {
    new Runner(new CommandLineOptions(args)).run();
  }

  private static String failureValue(int index) {
    return (index & 1) == 0 ? "invalid" : "70000";
  }

  private long processVanillaPort(String rawPort) {
    final int port;

    try {
      port = Integer.parseInt(rawPort.strip());
    } catch (NumberFormatException _) {
      return PortError.PARSE.checksum();
    }

    return port >= 1 && port <= 65_535
        ? new Endpoint(USERNAME, port).checksum()
        : PortError.VALIDATION.checksum();
  }

  private Result<Endpoint, PortError> processWithResult(String rawPort) {
    return Result.<Integer, PortError>from(
            () -> Integer.parseInt(rawPort.strip()), _ -> PortError.PARSE)
        .andThen(ResultBenchmark::validatePort)
        .map(port -> new Endpoint(USERNAME, port));
  }

  private long processVanillaPortWithoutExceptions(String rawPort) {
    final int port = parsePortWithoutExceptions(rawPort);

    if (port == PARSE_FAILED) {
      return PortError.PARSE.checksum();
    }

    return port >= 1 && port <= 65_535
        ? new Endpoint(USERNAME, port).checksum()
        : PortError.VALIDATION.checksum();
  }

  private Result<Endpoint, PortError> processWithResultWithoutExceptions(String rawPort) {
    final int port = parsePortWithoutExceptions(rawPort);
    final Result<Integer, PortError> parsed =
        port == PARSE_FAILED ? Result.err(PortError.PARSE) : Result.ok(port);

    return parsed
        .andThen(ResultBenchmark::validatePort)
        .map(value -> new Endpoint(USERNAME, value));
  }

  private long processVanillaBatch() {
    long checksum = 0;

    for (String rawPort : ports) {
      final int port = parsePortWithoutExceptions(rawPort);
      if (port == PARSE_FAILED) {
        return PortError.PARSE.checksum();
      }
      if (port < 1 || port > 65_535) {
        return PortError.VALIDATION.checksum();
      }
      checksum += new Endpoint(USERNAME, port).checksum();
    }

    return checksum;
  }

  private Result<BatchReport, PortError> processResultBatch() {
    long checksum = 0;

    for (String rawPort : ports) {
      final int port = parsePortWithoutExceptions(rawPort);
      if (port == PARSE_FAILED) {
        return Result.err(PortError.PARSE);
      }
      if (port < 1 || port > 65_535) {
        return Result.err(PortError.VALIDATION);
      }
      checksum += new Endpoint(USERNAME, port).checksum();
    }

    return Result.ok(new BatchReport(checksum));
  }

  private static int parsePortWithoutExceptions(String rawPort) {
    final String value = rawPort.strip();

    if (value.isEmpty()) {
      return PARSE_FAILED;
    }

    int port = 0;
    for (int index = 0; index < value.length(); index++) {
      final int digit = value.charAt(index) - '0';

      if (digit < 0 || digit > 9 || port > (Integer.MAX_VALUE - digit) / 10) {
        return PARSE_FAILED;
      }

      port = port * 10 + digit;
    }

    return port;
  }

  private static Result<Integer, PortError> validatePort(int port) {
    return port >= 1 && port <= 65_535 ? Result.ok(port) : Result.err(PortError.VALIDATION);
  }

  private static long endpointCode(String username, int port) {
    return 31L * username.hashCode() + port;
  }

  private record Endpoint(String username, int port) {

    long checksum() {
      return endpointCode(username, port);
    }
  }

  private record BatchReport(long checksum) {}

  private enum PortError {
    PARSE(PARSE_ERROR_CHECKSUM),
    VALIDATION(VALIDATION_ERROR_CHECKSUM);

    private final long checksum;

    PortError(long checksum) {
      this.checksum = checksum;
    }

    long checksum() {
      return checksum;
    }
  }
}
