package io.github.joseevb.result.yavi.examples;

import am.ik.yavi.arguments.Arguments3Validator;
import am.ik.yavi.builder.ValidatorBuilder;
import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.core.Validatable;
import am.ik.yavi.core.Validated;
import am.ik.yavi.validator.Yavi;
import io.github.joseevb.result.Result;
import io.github.joseevb.result.yavi.ValidatedResult;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;

/// Compares direct YAVI results with the equivalent ValidatedResult adapter paths.
///
/// Run with -PbenchmarkArgs="-prof gc" to include allocation rate and bytes per operation.
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(
    value = 2,
    jvmArgsAppend = {"-Xms1g", "-Xmx1g"})
@Threads(1)
@State(Scope.Benchmark)
public class ValidatedResultBenchmark {

  record User(String name, String email, int age) {}

  private static final Validatable<User> USER_VALIDATOR =
      ValidatorBuilder.<User>of()
          .constraint(User::name, "name", c -> c.notBlank())
          .constraint(User::email, "email", c -> c.notBlank().email())
          .constraint(User::age, "age", c -> c.greaterThanOrEqual(18))
          .build();

  private static final Arguments3Validator<String, String, Integer, User> USER_ARGUMENTS =
      Yavi.arguments()
          ._string("name", c -> c.notBlank())
          ._string("email", c -> c.notBlank().email())
          ._integer("age", c -> c.greaterThanOrEqual(18))
          .apply(User::new);

  @Param({"valid", "invalid"})
  private String input;

  private User user;
  private String name;
  private String email;
  private int age;

  @Setup
  public void setUp() {
    final boolean valid = input.equals("valid");
    name = valid ? "Jose" : "";
    email = valid ? "jose@example.com" : "invalid";
    age = valid ? 21 : 15;
    user = new User(name, email, age);

    final boolean objectIsValid = USER_VALIDATOR.validate(user).isValid();
    final boolean argumentsAreValid = USER_ARGUMENTS.validate(name, email, age).isValid();
    if (objectIsValid != valid || argumentsAreValid != valid) {
      throw new IllegalStateException("Benchmark inputs do not produce the expected outcome");
    }
  }

  @Benchmark
  public ConstraintViolations directObjectValidation() {
    return USER_VALIDATOR.validate(user);
  }

  @Benchmark
  public Result<User, ConstraintViolations> adaptedObjectValidation() {
    return ValidatedResult.validate(user, USER_VALIDATOR);
  }

  @Benchmark
  public Validated<User> directArgumentValidation() {
    return USER_ARGUMENTS.validate(name, email, age);
  }

  @Benchmark
  public Result<User, ConstraintViolations> adaptedArgumentValidation() {
    return ValidatedResult.from(USER_ARGUMENTS.validate(name, email, age));
  }

  public static void main(String... args) throws Exception {
    new Runner(new CommandLineOptions(args)).run();
  }
}
