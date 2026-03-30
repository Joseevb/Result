# Result

A modern, type-safe Result monad library for Java implementing Railway Oriented Programming. Eliminate exception-driven control flow with composable, compile-time checked error handling.

[![Java 25+](https://img.shields.io/badge/Java-25%2B-blue)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/dev.jose/result-core.svg)](https://central.sonatype.com/search?q=dev.jose)

## Overview

`Result` is a library that brings functional error handling to Java. Instead of throwing exceptions, operations return a `Result<T, E>` containing either a success value or a domain error. This library provides:

- A sealed `Result<T, E>` type for synchronous operations with a rich fluent API
- An async counterpart `AsyncResult<T, E>` for non-blocking Railway Oriented Programming
- A composable `Validator<T>` for declarative field-level validation
- A declarative `ErrorRouter<E>` for exception-to-domain-error mapping
- Spring Boot integration with automatic Result unwrapping and RFC 7807 Problem Details responses
- Zero required runtime dependencies in the core module

## Features

- **Type-Safe Error Handling** -- Compile-time verification of all error paths
- **Railway Oriented Programming** -- Chain operations without explicit error checks at each step
- **Async-First Design** -- `AsyncResult` supports parallel composition, retries with backoff, and timeouts
- **Declarative Validation** -- Fluent `Validator` with field-level error collection
- **Exception Mapping** -- `ErrorRouter` eliminates verbose try-catch-translate patterns
- **Spring Boot Integration** -- Automatic controller response conversion with RFC 7807 Problem Details, i18n, and metrics
- **Observability** -- Built-in Micrometer metrics and SLF4J logging hooks
- **Zero Core Dependencies** -- Only annotations are compile-only; bring your own SLF4J and Micrometer

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
public sealed interface UserError {
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
        return Result.attempt(
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
            .flatMap(validUser -> Result.attempt(
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
    .peek(email -> log.info("Found user: {}", email))
    .flatMap(email -> validateEmail(email))
    .recover(error -> "guest@example.com")
    .fold(
        email -> "Success: " + email,
        error -> "Failed: " + error.getMessage()
    );
```

## Real-World Recipe: Load User with Orders

This example demonstrates a typical service layer pattern - loading a user, fetching their orders in parallel, and combining the results:

```java
public AsyncResult<UserProfile, UserError> getUserWithOrders(Long userId) {
    // Start with fetching user and preferences in parallel
    return AsyncResult.sequence(List.of(
            userService.findByIdAsync(userId),
            preferencesService.getPreferencesAsync(userId)
        ))
        .combine(
            orderService.getRecentOrdersAsync(userId),
            (results, orders) -> {
                final User user = results.get(0);
                final UserPreferences prefs = results.get(1);
                return new UserProfile(user, prefs, orders);
            }
        )
        .filter(
            profile -> !profile.orders().isEmpty(),
            () -> new UserError.NoOrders(userId)
        )
        .peekFailure(err -> log.error("Failed to load profile for user {}: {}", userId, err));
}
```

### Key Patterns Demonstrated:
- **`sequence()`** - Parallel execution with fail-fast
- **`combine()`** - Merge independent results
- **`filter()`** - Validate business rules
- **`peekFailure()`** - Logging without breaking the chain

## API Reference

### Result<T, E>

The core synchronous result type. Represents a value that is either a success or a domain error.

#### Creation

```java
// Basic constructors
Result<User, UserError> success = Result.success(user);
Result<User, UserError> failure = Result.failure(new UserError.NotFound(id));

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

// Capture checked exceptions
Result<User, UserError> captured = Result.attempt(
    () -> repository.save(user),
    ex -> new UserError.DatabaseError(ex.getMessage())
);

// For void operations
Result<Void, UserError> voidOp = Result.empty();
```

#### Transformations

```java
// Transform the success value
Result<String, UserError> mapped = result.map(User::email);

// Transform the error
Result<User, ApiError> apiResult = result.mapError(
    err -> new ApiError("USER_ERROR", err.getMessage())
);

// Transform both paths
Result<UserDTO, ApiError> both = result.mapBoth(
    user -> new UserDTO(user.id(), user.email()),
    err -> new ApiError("FAIL", err.getMessage())
);

// Chain operations that return Results
Result<Boolean, UserError> chained = result
    .flatMap(user -> validateUser(user))  // returns Result<Boolean, UserError>
    .flatMap(isValid -> saveIfValid(isValid));

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
User user3 = result.unwrap();  // throws RuntimeException on Failure

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
// Peek at success without modifying
result
    .peek(user -> metrics.recordUser(user))
    .peek(user -> log.info("Loaded user: {}", user.id()));

// Peek at failure
result
    .peekFailure(error -> alerting.send("User load failed", error))
    .peekFailure(error -> log.error("Error: {}", error.getMessage()));
```

#### Bulk Operations

```java
// Collect results from a stream (short-circuit on first failure)
Stream<Result<User, UserError>> results = userIds.stream()
    .map(repository::findById);
Result<List<User>, UserError> allUsers = Result.collect(this.results);

// Flatten nested Results
Result<User, UserError> flat = Result.flatten(
    nestedResult  // Result<Result<User, UserError>, UserError>
);
```

### AsyncResult<T, E>

Non-blocking counterpart to `Result`. Represents a computation that will eventually produce a `Result<T, E>`. Supports parallel composition, retries with backoff, and timeouts.

#### Creation

```java
// From CompletableFuture, catching exceptions
AsyncResult<User, UserError> userAsync = AsyncResult.attempt(
    webClient.getUser(id),
    ex -> switch (ex) {
        case WebClientResponseException.NotFound _
            -> new UserError.NotFound(id);
        case WebClientResponseException.BadRequest _
            -> new UserError.InvalidInput("request", ex.getMessage());
        default -> new UserError.DatabaseError(ex.getMessage());
    }
);

// From existing CompletableFuture<Result<T, E>>
AsyncResult<User, UserError> fromFuture = AsyncResult.of(
    CompletableFuture.completedFuture(Result.success(user))
);

// From already-completed Result (useful for fallbacks)
AsyncResult<User, UserError> cached = AsyncResult.completed(
    Result.success(cachedUser)
);

// Pre-built success/failure
AsyncResult<User, UserError> s = AsyncResult.success(user);
AsyncResult<User, UserError> f = AsyncResult.failure(new UserError.NotFound(id));
```

#### Chaining

```java
// Transform the success value
AsyncResult<String, UserError> email = userAsync.map(User::email);

// Chain async operations
AsyncResult<Order, UserError> order = userAsync
    .flatMap(user -> fetchCart(user.id()))      // AsyncResult<Cart, UserError>
    .flatMap(cart -> createOrder(cart))         // AsyncResult<Order, UserError>
    .map(Order::withTimestamp);

// Combine two independent async operations (parallel)
AsyncResult<UserProfile, UserError> profile = userAsync.combine(
    preferencesAsync,
    (user, prefs) -> new UserProfile(user, prefs)
);

// Transform the error
AsyncResult<User, ApiError> apiAsync = userAsync.mapError(
    err -> new ApiError("USER_ERROR", err.getMessage())
);
```

#### Composition

```java
// Collect multiple async results in parallel
List<AsyncResult<Product, ShopError>> fetches = productIds.stream()
    .map(this::fetchProduct)
    .toList();
AsyncResult<List<Product>, ShopError> allProducts = AsyncResult.collectAll(this.fetches);

// Semantic alias - clearer when sequencing operations
AsyncResult<List<Product>, ShopError> sequenced = AsyncResult.sequence(this.fetches);

// Race multiple async results (first to complete wins)
AsyncResult<User, UserError> fastest = AsyncResult.race(
    fetchFromCache(id),
    fetchFromDatabase(id),
    fetchFromReplica(id)
);
```

#### Resilience

```java
// Timeout with fallback error
AsyncResult<User, UserError> timed = userAsync.timeout(
    Duration.ofSeconds(5),
    () -> new UserError.DatabaseError("Request timeout")
);

// Retry with fixed attempts (supply fresh AsyncResult each time)
AsyncResult<User, UserError> retry3 = AsyncResult.retry(() -> fetchUser(id), 3);

// Retry with exponential backoff
AsyncResult<User, UserError> resilient = AsyncResult.retryWithBackoff(
    () -> fetchUser(id),           // Supplier: fresh attempt each time
    3,                            // max attempts
    Duration.ofMillis(100)        // initial delay, doubled each retry
);

// Retry with custom backoff multiplier
AsyncResult<User, UserError> custom = AsyncResult.retryWithBackoff(
    () -> fetchUser(id),
    5,
    Duration.ofMillis(50),
    3.0  // triple the delay each time
);

// Delay execution
AsyncResult<User, UserError> delayed = AsyncResult.delay(
    userAsync,
    Duration.ofSeconds(1)
);
```

#### Terminal Operations

```java
// Block and await (use at application boundaries)
Result<User, UserError> result = userAsync.join();

// Check completion status
if (userAsync.isDone()) {
    // ...
}

// Convert to CompletableFuture
CompletableFuture<Result<User, UserError>> future = userAsync.toFuture();

// Cancellation support
boolean cancelled = userAsync.cancel(true);  // Attempt to cancel running operation
if (userAsync.isCancelled()) {
    // Operation was cancelled
}

// Note: Cancellation is best-effort. Chained operations (map, flatMap, etc.)
// will complete with CancellationException if upstream is cancelled.
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
    .withLogging(log)  // optional: log exceptions at WARN
    .withMetrics(meterRegistry, "user.errors")  // optional: Micrometer metrics
    .withShadowWarnings()  // optional: warn on shadowing during config
    .map(IllegalArgumentException.class,
        ex -> new UserError.InvalidInput("field", ex.getMessage()))
    .map(DataIntegrityViolationException.class,
        _ -> new UserError.InvalidInput("email", "Already exists"))
    .map(TimeoutException.class,
        _ -> new UserError.DatabaseError("Request timeout"));

// Invoke in Result.attempt()
Result<User, UserError> result = Result.attempt(
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

// Map exception hierarchy (alias for clarity)
router.mapAll(IOException.class,
    ex -> new UserError.DatabaseError(ex.getMessage())
);

// Map with side effects (logging, alerting)
router.mapWithEffect(
    SQLException.class,
    (ex, error) -> alerting.notifyCritical("Database error", ex),
    ex -> new UserError.DatabaseError(ex.getMessage())
);

// Freeze for concurrent use (enables O(1) lookup caching)
// Recommended for high-throughput scenarios - call after configuration
router.freeze();
```

#### Introspection

```java
int mappingCount = router.mappingCount();
boolean hasMapping = router.hasMappingFor(IllegalArgumentException.class);
```

## Spring Boot Integration

### Setup

Register the `ResultResponseAdvice` bean in your configuration:

```java
@Configuration
public class ResultConfig {
    @Bean
    public ResultResponseAdvice resultAdvice(
        MessageSource messageSource,
        MeterRegistry meterRegistry
    ) {
        return new ResultResponseAdvice(messageSource, meterRegistry);
    }
}
```

The advice is auto-configured if you depend on `result-springboot` with proper Spring Boot version.

### Define Domain Errors

Implement `BaseFailure` (and optionally `HttpFailure`) for automatic Spring integration:

```java
public sealed interface UserFailure extends BaseFailure {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    record NotFound(Long userId) implements UserFailure {
        @Override
        public String getMessage() {
            return "User " + this.userId + " not found";
        }

        @Override
        public Map<String, Object> getExtensions() {
            return Map.of("userId", this.userId);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    record ValidationFailed(Map<String, String> errors)
        implements UserFailure, HttpFailure {

        @Override
        public String getMessage() {
            return "Validation failed";
        }

        @Override
        public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
            return Optional.of(this.createValidationError(this.errors));
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    record EmailExists(String email) implements UserFailure, HttpFailure {
        @Override
        public String getMessage() {
            return "Email " + this.email + " is already in use";
        }

        @Override
        public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
            return Optional.of(this.createConflictError(
                "Email already registered",
                this.email
            ));
        }
    }
}
```

### Controller Example

Controllers return `Result` or `AsyncResult` directly. The advice handles unwrapping and response conversion:

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
            .mapError(errors -> new UserFailure.ValidationFailed(errors))
            .flatMap(validReq -> this.userService.create(validReq))
            .map(user -> new UserDTO(user.getId(), user.getEmail()));
    }

    @PutMapping("/{id}")
    public AsyncResult<UserDTO, UserFailure> updateUser(
        @PathVariable Long id,
        @RequestBody UpdateUserRequest req
    ) {
        return this.userService.findByIdAsync(id)
            .flatMap(user -> this.userService.validateAndUpdateAsync(user, req))
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
