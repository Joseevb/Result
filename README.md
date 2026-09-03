# Result

A small Java library for explicit success and failure values. `Result<T, E>` is a sealed type with
an `Ok` value of type `T` or an `Err` value of type `E`.

[![Java 25+](https://img.shields.io/badge/Java-25%2B-blue)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.joseevb/result-core.svg)](https://central.sonatype.com/search?q=io.github.joseevb)

## Requirements

- Java 25
- Gradle 9.x for building this repository
- Spring Boot 4.0.3 for the tested Spring MVC integration module

The repository is built with Gradle. The published artifacts can also be consumed from Maven.

## Installation

### Gradle

```kotlin
dependencies {
    implementation("io.github.joseevb:result-core:0.1.0-SNAPSHOT")
}
```

For servlet-based Spring MVC response handling, add the integration module and provide the Spring
Boot MVC dependencies in the application:

```kotlin
dependencies {
    implementation("io.github.joseevb:result-springboot:0.1.0-SNAPSHOT")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.joseevb</groupId>
    <artifactId>result-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The Spring MVC integration artifact is:

```xml
<dependency>
    <groupId>io.github.joseevb</groupId>
    <artifactId>result-springboot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The integration module declares Spring Boot dependencies as `compileOnly`; the consuming
application must provide compatible Spring MVC and Spring Boot dependencies.

## Basic Example

The typed `Result.from` overload catches an `Exception` and maps it to the declared error type.
`Error` instances are not caught. Pattern matching makes both outcomes explicit:

```java
import io.github.joseevb.result.Result;

sealed interface ParseError {
    record InvalidNumber() implements ParseError {}
}

// Vanilla Java
void vanillaJava(String input) {
    try {
        IO.println(Integer.parseInt(input));
    } catch (NumberFormatException e) {
        System.err.println("Invalid number");
    }
}

// With Result
void resultJava(String input) {
    var result = Result.from(
        () -> Integer.parseInt(input),
        _ -> new ParseError.InvalidNumber()
    );

    switch (result) {
        case Result.Ok(var number) -> IO.println(number);
        case Result.Err(var error) -> System.err.println("Invalid number");
    }
}
```

`Ok` and `Err` reject `null`. A supplier returning `null`, an error supplier returning `null`, or a
typed exception mapper returning `null` therefore fails with `NullPointerException`.

## `Result<T, E>`

`Result` is a sealed interface with these public variants:

```java
Result.Ok<T, E>(T value)
Result.Err<T, E>(E error)
Result.Unit.INSTANCE
```

The static factories are:

```java
Result<Integer, String> ok = Result.ok(42);
Result<Integer, String> err = Result.err("invalid number");
Result<Integer, String> nullable = Result.ofNullable(
    possiblyNull,
    () -> "value was null"
);
Result<Result.Unit, String> empty = Result.empty();
```

`Result.from(action)` returns `Result<T, Exception>`. The typed overload returns `Result<T, E>`:

```java
import java.util.Optional;

Result<Integer, Exception> captured = Result.from(() -> Integer.parseInt(input));
Result<Integer, ParseError> mapped = Result.from(
    () -> Integer.parseInt(input),
    _ -> new ParseError.InvalidNumber()
);

Result<Integer, ParseError> fromOptional = Result.fromOptional(
    Optional.of(42),
    () -> new ParseError.InvalidNumber()
);
```

Both overloads catch only `Exception` thrown by the supplier. They do not catch `Error`.
The supplier type is `Result.ThrowingSupplier<T>`, a functional interface whose `get()` method may
throw a checked `Exception`.

### Transforming Results

```java
Result<Integer, ParseError> parsed = Result.ok(10);
Result<String, ParseError> text = parsed.map(String::valueOf);
Result<Integer, String> translated = parsed.mapErr(error -> "parse failed");
Result<String, String> both = parsed.map(
    String::valueOf,
    error -> "parse failed"
);
```

Use `andThen` when the next operation already returns a `Result`:

```java
Result<Integer, ParseError> validated = parsed.andThen(value ->
    value >= 0
        ? Result.ok(value)
        : Result.err(new ParseError.InvalidNumber())
);
```

For an `Err`, `map`, `andThen`, and the success side of `map` pass the error through. For an `Ok`,
`mapErr` passes the value through.

`combine` evaluates the combiner only when both Results are `Ok`. Otherwise it returns the first
`Err` in left-to-right order:

```java
Result<String, String> combined = Result.ok("user").combine(
    Result.ok("profile"),
    (user, profile) -> user + ":" + profile
);
```

### Recovery and Inspection

```java
Result<Integer, ParseError> recovered = parsed.recover(error -> 0);
Result<Integer, ParseError> retried = parsed.recoverWith(error -> parseAgain());

parsed
    .inspect(value -> println("value=" + value))
    .inspectErr(error -> System.err.println("error=" + error));
```

`recover` converts an `Err` to an `Ok`. `recoverWith` replaces an `Err` with another Result and
requires its callback to return a non-null Result. Both leave an `Ok` unchanged. `inspect` and
`inspectErr` return the same Result instance after conditionally running their callback.

Use `isOk()` and `isErr()` for state checks. To handle both states in one expression, use `fold`:

```java
String message = parsed.fold(
    value -> "value=" + value,
    error -> "error=" + error
);
```

### Extraction and Conversion

```java
int value = parsed.unwrapOr(0);
int fallback = parsed.unwrapOrElse(this::defaultValue);
int unsafe = parsed.unwrap();
int mapped = parsed.unwrapOrThrow(error -> new IllegalStateException("parse failed"));

Stream<Integer> stream = parsed.stream();
Optional<Integer> optional = parsed.toOptional();
```

`unwrap` throws `RuntimeException` for an `Err`. `unwrapOrThrow` throws the `RuntimeException`
returned by its mapper. Prefer `fold`, pattern matching, or a recovery operation when the error case
is expected.

### Collecting and Sequencing

`collect(Stream)` produces a `List` and stops at the first `Err`. The collector overload supports a
different result container:

```java
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

List<String> inputs = List.of("10", "20", "not-a-number");

Result<List<Integer>, ParseError> values = Result.collect(
    inputs.stream().map(input -> Result.from(
        () -> Integer.parseInt(input),
        _ -> new ParseError.InvalidNumber()
    ))
);

Result<Set<Integer>, ParseError> unique = Result.collect(
    inputs.stream().map(input -> Result.from(
        () -> Integer.parseInt(input),
        _ -> new ParseError.InvalidNumber()
    )),
    Collectors.toSet()
);
```

`sequence(List<Result<T, E>>)` and its varargs overload are equivalent for a list or array of
Results:

```java
Result<List<Integer>, ParseError> sequenced = Result.sequence(List.of(
    Result.ok(1),
    Result.ok(2),
    Result.ok(3)
));
```

Both operations return the first `Err` and otherwise return an `Ok` containing all values.
`flatten` converts `Result<Result<T, E>, E>` into `Result<T, E>`.

## `Validator<T, E>`

`Validator` is an immutable, two-parameter validator. It stores errors in a `Map<String, E>` keyed
by field name. Adding another error for the same field replaces that field's previous error.

```java
record User(String email, String name, int age) {}
record ValidationError(String code, String message) {}

Validator<User, ValidationError> validator = Validator
    .<User, ValidationError>of(user)
    .validate(
        value -> value.age() >= 18,
        "age",
        new ValidationError("AGE_MIN", "Must be 18+")
    )
    .nonNull(
        User::email,
        "email",
        new ValidationError("EMAIL_REQUIRED", "Email is required")
    )
    .matches(
        User::email,
        "^[A-Za-z0-9+_.-]+@(.+)$",
        "email",
        new ValidationError("EMAIL_FORMAT", "Invalid email")
    )
    .length(
        User::name,
        1,
        100,
        "name",
        new ValidationError("NAME_LENGTH", "Invalid name length")
    )
    .range(
        User::age,
        0,
        150,
        "age",
        new ValidationError("AGE_RANGE", "Invalid age")
    );
```

The available validation methods are:

- `validate(Predicate<T>, String, E)`
- `validate(Predicate<T>, String, Supplier<E>)`
- `validateIf(Predicate<T>, UnaryOperator<Validator<T, E>>)`
- `nonNull(Function<T, U>, String, E)`
- `matches(Function<T, String>, String pattern, String, E)`
- `range(Function<T, N>, double min, double max, String, E)`
- `length(Function<T, String>, int min, int max, String, E)`

`matches`, `range`, and `length` treat a null extracted value as a validation failure. Their bounds
are inclusive. The supplier overload of `validate` computes its error only when the predicate fails.
`validateIf` applies its validation block only when its condition is true.

Convert a validator to a Result with either the default error map or a mapped error type:

```java
Result<User, Map<String, ValidationError>> result = validator.result();
Result<User, ValidationError> collapsed = validator.resultOr(
    errors -> errors.values().stream().findFirst().orElseThrow()
);
```

`result()` and `resultOr(...)` return an `Ok` containing the target when there are no errors. With
errors, they return an `Err` containing an unmodifiable copy of the error map or the mapped error.
`errors()` returns an unmodifiable view of the current map. `hasErrors()` and `errorCount()` expose
its state. `compose(target, validations...)` applies validation functions in order.

## `ErrorRouter<E>`

`ErrorRouter` is an immutable `Function<Exception, E>` for mapping exceptions to domain errors.
Rules are checked in registration order, and the first matching rule wins. A rule matches its
registered exception type and subclasses.

```java
sealed interface AppError {
    record InvalidInput() implements AppError {}
    record DatabaseFailure() implements AppError {}
    record Unexpected() implements AppError {}
}

var router = ErrorRouter
    .<AppError>defaultsTo(_ -> new AppError.Unexpected())
    .map(NumberFormatException.class, _ -> new AppError.InvalidInput())
    .map(Exception.class, _ -> new AppError.DatabaseFailure());

AppError error = router.apply(new NumberFormatException());
int rules = router.ruleCount();
boolean mapsNumbers = router.hasRuleFor(NumberFormatException.class);
```

Register specific exception types before general types. `defaultsTo` is the required entry point;
the fallback is used when no explicit rule matches.

## Spring MVC Integration

The `result-springboot` module provides `ResultResponseAdvice` for servlet-based Spring MVC only.
It is auto-configured when the application is a servlet web application, Spring MVC's
`ResponseBodyAdvice` is available, and no application-provided advice takes precedence.

Controller methods may return:

- `Result<T, E>`
- `ResponseEntity<Result<T, E>>`

An `Ok` is unwrapped to its value. An `Err` is converted to a Spring `ProblemDetail` only when its
error implements `Failure`; otherwise an `IllegalStateException` is thrown. A failure uses its
`@ResponseStatus` code, or HTTP 500 when it has no annotation. `ResponseEntity` headers are
preserved, while an `Err` sets the response status to the failure status.

```java
import io.github.joseevb.result.Result;
import io.github.joseevb.result.spring.Failure;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
record UserNotFound(long userId) implements Failure {
    @Override
    public String getMessage() {
        return "User " + userId + " was not found";
    }
}
```

`Failure` requires `getMessage()`. Its defaults provide:

- `getMessageArgs()` as an empty array
- `getTitle()` from the failure class's simple name
- `getErrorCode()` as the failure class name converted to upper snake case
- `getExtensions()` as an empty map

Problem details use `error.<FailureSimpleName>` for the localized detail and
`error.title.<FailureSimpleName>` for the localized title. `getMessageArgs()` is supplied only to
the detail message. Missing messages fall back to `getMessage()` and `getTitle()`.

## Examples And Benchmarks

List the core examples:

```shell
./gradlew :result-core:examples
```

Run the pipeline example:

```shell
./gradlew :result-core:examples -PexampleArgs='pipeline --impl=both'
```

Run the JMH benchmark:

```shell
./gradlew :result-core:benchmark
```

The examples compare ordinary Java control flow with `Result` for file I/O, parsing, validation,
recovery, combining values, and bulk processing. The benchmark compares vanilla Java,
`Result`, and an intentionally optimized implementation across success and failure rates.

## Development

```shell
./gradlew build
./gradlew test
```

The project is licensed under the MIT License. See [LICENSE](LICENSE).
