# YAVI integration

`result-core` includes a small, dependency-free `Validator<T, E>` for simple local validation. The
optional `result-yavi` module is intended for applications that already use YAVI or need a fuller
validation framework, such as reusable constraints, nested or collection validation, conditional
validation, argument validation, localization, or constraint contexts.

The two APIs are complementary. The built-in validator is not deprecated or a reduced form of the
YAVI integration.

## Installation

`result-yavi` is introduced in Result 0.2.0.

Gradle:

```kotlin
dependencies {
    implementation("io.github.joseevb:result-yavi:0.2.0")
}
```

Maven:

```xml
<dependency>
    <groupId>io.github.joseevb</groupId>
    <artifactId>result-yavi</artifactId>
    <version>0.2.0</version>
</dependency>
```

`result-yavi` exposes YAVI because YAVI types appear in the adapter's public signatures. YAVI is not
a dependency of `result-core`.

## Lightweight validation with result-core

For a few application-specific checks, the built-in validator keeps validation inside the Result
library without adding another dependency:

```java
Result<User, Map<String, UserError>> result =
    Validator.<User, UserError>of(user)
        .validate(User::hasValidName, "name", UserError.INVALID_NAME)
        .validate(User::hasValidEmail, "email", UserError.INVALID_EMAIL)
        .result();
```

## Adapting YAVI validation

YAVI remains responsible for validation. `ValidatedResult` only adapts YAVI's outcome into
`Result<T, ConstraintViolations>`.

Validate an existing object:

```java
Result<User, ConstraintViolations> result =
    ValidatedResult.validate(user, USER_VALIDATOR);
```

Use YAVI argument validation before constructing an object:

```java
Result<User, ConstraintViolations> result =
    ValidatedResult.from(USER_ARGUMENTS.validate(name, email, age));
```

`ConstraintViolations` are preserved as YAVI returned them. If the application wants a domain error,
map it with normal Result composition:

```java
Result<User, UserError> result =
    ValidatedResult.validate(user, USER_VALIDATOR)
        .mapErr(UserError.Validation::new)
        .andThen(service::createUser);
```

This keeps the responsibilities separate:

```text
YAVI            -> performs validation
ValidatedResult -> adapts the validation outcome
Result           -> composes application control flow and domain errors
```

## Null semantics

`Result.Ok` and `Result.Err` never contain `null`. YAVI's `Validated<T>` can represent a successful
nullable value with `Validated.successWith(null)`. Converting that value through
`ValidatedResult.from(...)` therefore throws `NullPointerException` rather than producing an invalid
`Result.ok(null)`.

Normal object validation follows YAVI's target-null contract. The adapter does not weaken Result's
non-null payload invariant to accommodate YAVI.

## Choosing between the validators

Use `Validator<T, E>` when validation is small, local, and naturally expressed as a few predicates.
Use `result-yavi` when the application benefits from YAVI's richer validation model. Installing YAVI
is not a prerequisite or recommended default for using `Result`.
