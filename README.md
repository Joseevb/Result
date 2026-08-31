# Result

A modern, type-safe Result monad library for Java implementing Railway Oriented Programming. Eliminate exception-driven control flow with composable, compile-time checked error handling.

[![Java 25+](https://img.shields.io/badge/Java-25%2B-blue)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/dev.jose/result-core.svg)](https://central.sonatype.com/search?q=dev.jose)

## Overview

`Result` is a library that brings functional error handling to Java. Instead of throwing exceptions, operations return a `Result<T, E>` containing either a success value or a domain error. This library provides:

- A sealed `Result<T, E>` type for synchronous operations with a rich fluent API
- A composable `Validator<T>` for declarative field-level validation
- A declarative `ErrorRouter<E>` for exception-to-domain-error mapping
- Spring Boot integration with automatic Result unwrapping and RFC 7807 Problem Details responses
- Zero required runtime dependencies in the core module

## Features

- **Type-Safe Error Handling** -- Compile-time verification of all error paths
- **Railway Oriented Programming** -- Chain operations without explicit error checks at each step
- **Declarative Validation** -- Fluent `Validator` with field-level error collection
- **Exception Mapping** -- `ErrorRouter` eliminates verbose try-catch-translate patterns
- **Spring Boot Integration** -- Automatic controller response conversion with RFC 7807 Problem Details and i18n
- **Zero Core Dependencies** -- Nullability annotations are compile-only

## Requirements

- **Java 25+** (uses pattern matching, sealed interfaces, records)
- **Gradle 9.x** or **Maven 3.8+**
- **Spring Boot 4.x** (optional, only for `result-springboot` integration)

## Installation

### Gradle (Kotlin DSL)

Core library:

```kotlin
dependencies {
    implementation("dev.jose:result-core:0.1.0-SNAPSHOT")
}
```

Spring Boot integration:

```kotlin
dependencies {
    implementation("dev.jose:result-springboot:0.1.0-SNAPSHOT")
}
```

### Maven

Core library:

```xml
<dependency>
    <groupId>dev.jose</groupId>
    <artifactId>result-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Spring Boot integration:

```xml
<dependency>
    <groupId>dev.jose</groupId>
    <artifactId>result-springboot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### Define Domain Errors

Start with a sealed interface representing all possible errors your operation can produce:

```java
public sealed interface UserError implements Failure {
    @ResponseStatus(HttpStatus.NOT_FOUND)
    record NotFound(Long id) implements UserError {}

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    record InvalidInput(String field, String reason) implements UserError {}

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    record DatabaseError(String message) implements UserError {}

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    record UnknownError(String message, Throwable cause) implements UserError {}

    default String getMessage() {
        return switch(this) {
            case NotFound(var id) -> "User with id %s not found".formatted(id);
            case InvalidInput(var field, var reason) -> "%s: %s".formatted(field, reason);
            case DatabaseError(String message) -> message;
            default UnknownError(var message, _) -> "Unknown error: %s".formatted(message);
        }
    }
}
```

### Use Result in Your Service

```java
public class UserService {
    public Result<User, UserError> findById(Long id) {
        return Result.from(
            () -> repository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id)),
            exception -> new UserError.DatabaseError(exception.getMessage())
        );
    }

    public Result<User, UserError> validateAndSave(User user) {
        return Validator.of(user)
            .required(User::email, "email")
            .matches(User::email, "^[A-Za-z0-9+_.-]+@(.+)$", "email", "Invalid email format")
            .length(User::name, 1, 100, "name")
            .result()
            .andThen(validUser -> Result.from(
                () -> repository.save(validUser),
                ex -> new UserError.DatabaseError(ex.getMessage())
            ));
    }
}
```

### Chain Operations

```java
var response = userService.findById(42)
    .map(User::email)
    .inspect(email -> log.info("Found user: {}", email))
    .andThen(email -> validateEmail(email))
    .recover(error -> "guest@example.com")
    .fold(
        email -> "Ok: " + email,
        error -> "Failed: " + error.getMessage()
    );
```

## API Reference

### Result<T, E>

The core synchronous result type. It is either an `Ok` value or an `Err` domain error.
The error type `E` is commonly a sealed domain-error hierarchy.

`Ok` and `Err` reject `null`. For successful operations without a meaningful value, use
`empty()`, which produces `Result<Unit, E>` containing `Result.Unit.INSTANCE`.

#### Creation

```java
// Basic constructors
Result<User, UserError> okResult = Result.ok(user);
Result<User, UserError> errResult = Result.err(new UserError.NotFound(id));

// From Optional
Result<User, UserError> fromOpt = Result.fromOptional(
    repository.findById(id),
    () -> new UserError.NotFound(id)
);

// From nullable
Result<User, UserError> fromNull = Result.ofNullable(
    possiblyNull,
    () -> new UserError.InvalidInput("field", "null not allowed")
);

// Capture exceptions as Result<T, Exception>
Result<User, Exception> captured = Result.from(() -> repository.save(user));

// Typed error conversion path
Result<User, UserError> capturedTyped = Result.from(
    () -> repository.save(user),
    ex -> new UserError.DatabaseError(ex.getMessage())
);

// For operations without a meaningful value
Result<Result.Unit, UserError> voidOp = Result.empty();
```

#### Transformations

```java
// Transform the Ok value
Result<String, UserError> mapped = result.map(User::email);

// Transform the Err value
Result<User, ApiError> apiResult = result.mapErr(
    err -> new ApiError("USER_ERROR", err.getMessage())
);

// Transform both Ok and Err paths
Result<UserDTO, ApiError> both = result.map(
    user -> new UserDTO(user.id(), user.email()),
    err -> new ApiError("FAIL", err.getMessage())
);

// Chain operations that return Results
Result<Boolean, UserError> chained = result
    .andThen(user -> validateUser(user))  // returns Result<Boolean, UserError>
    .andThen(isValid -> saveIfValid(isValid));

// Combine two independent Results
Result<UserProfile, UserError> combined = userResult.combine(
    preferencesResult,
    (user, prefs) -> new UserProfile(user, prefs)
);

// Filter with a predicate
Result<User, UserError> activeOnly = result.filter(
    User::isActive,
    () -> new UserError.InvalidInput("user", "User is inactive")
);
```

#### Recovery

```java
// Recover with a fallback value
Result<User, UserError> withFallback = result.recover(
    error -> User.guest()
);

// Recover with another Result
Result<User, UserError> withRecovery = result.recoverWith(
    error -> cacheService.findById(id)
);
```

#### Terminal Operations

```java
// Exit the monad: fold both paths
String message = result.fold(
    user -> "Found: " + user.name(),
    error -> "Error: " + error.getMessage()
);

// Safe extraction
User user = result.unwrapOr(User.guest());
User user2 = result.unwrapOrElse(() -> loadFromCache());

// Unsafe extraction (use only in tests)
User user3 = result.unwrap();  // throws RuntimeException on Err

// Custom exception mapping
User user4 = result.unwrapOrThrow(
    err -> new NotFoundException(err.getMessage())
);

// Convert to stream or optional
Stream<User> stream = result.stream();
Optional<User> opt = result.toOptional();
```

#### Side Effects

```java
// Inspect an Ok without modifying it
result
    .inspect(user -> metrics.recordUser(user))
    .inspect(user -> log.info("Loaded user: {}", user.id()));

// Inspect an Err without modifying it
result
    .inspectErr(error -> alerting.send("User load failed", error))
    .inspectErr(error -> log.error("Error: {}", error.getMessage()));
```

#### Bulk Operations

```java
// Collect results from a stream (short-circuit on the first Err)
Stream<Result<User, UserError>> results = userIds.stream()
    .map(repository::findById);
Result<List<User>, UserError> allUsers = Result.collect(results);

// Flatten nested Results
Result<User, UserError> flat = Result.flatten(
    nestedResult  // Result<Result<User, UserError>, UserError>
);
```

### Validator<T>

Fluent, immutable validator for collecting field-level errors and returning a `Result`.

#### Creation & Composition

```java
// Single validation
Validator<User> validator = Validator.of(user)
    .required(User::email, "email")
    .nonNull(User::name, "name")
    .length(User::name, 1, 100, "name");

// Compose multiple validators
Validator<User> composed = Validator.compose(user,
    v -> v.required(User::email, "email")
          .matches(User::email, "^[A-Za-z0-9+_.-]+@(.+)$", "email", "Invalid format"),
    v -> v.length(User::name, 1, 100, "name")
          .positive(User::age, "age")
);
```

#### Built-In Validations

```java
// String validations
validator
    .required(User::email, "email")              // not null/blank
    .matches(User::email, ".*@.*", "email", "Invalid email")  // regex
    .length(User::name, 1, 100, "name");        // length bounds

// Numeric validations
validator
    .positive(User::age, "age")                 // > 0
    .range(User::score, 0, 100, "score");      // 0-100 inclusive

// Null checks
validator
    .nonNull(User::profile, "profile");

// Optional fields
validator
    .validateOptional(
        User::middleName,
        name -> name.length() > 1,
        "middleName",
        "Must be at least 2 characters"
    );
```

#### Custom Validations

```java
// Predicate-based
validator.validate(
    user -> user.passwordHash() != null,
    "password",
    "Password is required"
);

// Conditional validation
validator.validateIf(
    user -> user.isPremium(),
    v -> v.nonNull(User::billingAddress, "billingAddress")
);

// Cross-field constraints
validator.validateFields(
    user -> user.password().equals(user.confirmPassword()),
    "Passwords must match",
    "password", "confirmPassword"
);
```

#### Result Conversion

```java
// Standard Result with error map
Result<User, Map<String, String>> result = validator.result();

// Custom error type
Result<User, ValidationError> result = validator.resultOr(
    errors -> new ValidationError(errors)
);

// Error inspection
if (validator.hasErrors()) {
    int count = validator.errorCount();
    // ...
}
```

### ErrorRouter<E>

Declarative exception-to-domain-error mapper. Eliminates verbose try-catch-translate patterns in service layers.

#### Creation & Configuration

```java
// Define once, reuse everywhere
Function<Exception, UserError> errorRouter = ErrorRouter
    .defaultsTo(ex -> new UserError.DatabaseError(ex.getMessage()))
    .map(IllegalArgumentException.class,
        ex -> new UserError.InvalidInput("field", ex.getMessage()))
    .map(DataIntegrityViolationException.class,
        _ -> new UserError.InvalidInput("email", "Already exists"))
    .map(TimeoutException.class,
        _ -> new UserError.DatabaseError("Request timeout"));

// Invoke in Result.from()
Result<User, UserError> result = Result.from(
    () -> repository.save(user),
    this.errorRouter
);
```

#### Mapping Rules

```java
// Map specific exception type
router.map(IllegalArgumentException.class,
    ex -> new UserError.InvalidInput("input", ex.getMessage())
);

// Exception subclasses are matched by their declared supertype
router.map(IOException.class,
    ex -> new UserError.DatabaseError(ex.getMessage())
);
```

#### Introspection

```java
int ruleCount = router.ruleCount();
boolean hasRule = router.hasRuleFor(IllegalArgumentException.class);
```

## Spring Boot Integration

### Setup

Register the `ResultResponseAdvice` bean in your configuration:

```java
@Configuration
public class ResultConfig {
    @Bean
    public ResultResponseAdvice resultAdvice(MessageSource messageSource) {
        return new ResultResponseAdvice(messageSource);
    }
}
```

The advice is auto-configured if you depend on `result-springboot` with proper Spring Boot version.

### Define Domain Errors

Implement the framework-agnostic `Failure` interface for automatic Spring integration. A sealed
domain-error hierarchy keeps the possible failures explicit:

```java
public sealed interface UserFailure extends Failure {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    record NotFound(Long userId) implements UserFailure {
        @Override
        public String getMessage() {
            return "User " + this.userId + " not found";
        }

        @Override
        public String getTitle() {
            return "Not Found";
        }

        @Override
        public Map<String, Object> getExtensions() {
            return Map.of("userId", this.userId);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    record ValidationFailed(Map<String, String> errors) implements UserFailure {

        @Override
        public String getMessage() {
            return "Validation failed";
        }

        @Override
        public String getTitle() {
            return "Bad Request";
        }

        @Override
        public Map<String, Object> getExtensions() {
            return Map.of("errors", this.errors);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    record EmailExists(String email) implements UserFailure {
        @Override
        public String getMessage() {
            return "Email " + this.email + " is already in use";
        }

        @Override
        public Map<String, Object> getExtensions() {
            return Map.of("email", this.email);
        }
    }
}
```

For localization, define `error.<FailureSimpleName>` and optionally
`error.title.<FailureSimpleName>` message keys. `getMessageArgs()` supplies interpolation values;
missing keys fall back to `getMessage()` and `getTitle()`.

### Controller Example

Controllers return `Result<T, E>` directly. `ResponseEntity<Result<T, E>>` is also supported when
headers or an outer success status are needed. `Result<ResponseEntity<T>, E>` is not treated specially.

```java
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public Result<UserDTO, UserFailure> getUser(@PathVariable Long id) {
        return this.userService.findById(id)
            .map(user -> new UserDTO(user.getId(), user.getEmail()));
    }

    @PostMapping
    public Result<UserDTO, UserFailure> createUser(@RequestBody CreateUserRequest req) {
        return Validator.of(req)
            .required(CreateUserRequest::email, "email")
            .matches(CreateUserRequest::email, ".*@.*", "email", "Invalid email")
            .result()
            .mapErr(errors -> new UserFailure.ValidationFailed(errors))
            .andThen(validReq -> this.userService.create(validReq))
            .map(user -> new UserDTO(user.getId(), user.getEmail()));
    }
}
```

### RFC 7807 Problem Details Output

On error, the advice automatically returns a Problem Details response:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "User 123 not found",
  "instance": "/users/123",
  "errorCode": "NOT_FOUND",
  "userId": 123
}
```

For validation errors:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/users",
  "errorCode": "VALIDATION_FAILED",
  "errors": {
    "email": "Invalid email format",
    "name": "Length must be between 1 and 100"
  }
}
```

## Contributing

Contributions are welcome. To develop:

1. Clone the repository
2. Build with Gradle: `./gradlew build`
3. Run tests: `./gradlew test`
4. Submit pull requests against the main branch

Follow existing code style and include tests for new functionality.

## License

MIT License. See LICENSE file for details.

---

For full API documentation, see the generated JavaDoc in each source file.
