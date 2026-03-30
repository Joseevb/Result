package dev.jose.result;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/// A **type-safe, immutable** validator that accumulates errors into a generic error type.
///
/// Unlike validators that lock you into `String` or `List<String>` errors, this class
/// allows you to specify your own error representation via the type parameter `E`.
/// This enables rich error types with codes, severity levels, i18n keys,
/// or any structure your application requires.
///
/// ## Basic Usage
///
/// ```java
/// record ValidationError(String code, String message, Severity severity) {}
///
/// var result = Validator.<User, ValidationError>of(user)
///     .validate(u -> u.age() >= 18, "age",
///         new ValidationError("AGE_MIN", "Must be 18+", Severity.ERROR))
///     .validate(u -> u.email().contains("@"), "email",
///         new ValidationError("EMAIL_FORMAT", "Invalid email", Severity.ERROR))
///     .result();
/// ```
///
/// ## Immutable Design
///
/// Every validation method returns a **new** `Validator` instance — the original
/// is never modified. This makes all instances safe to share across threads and
/// enables composable, branching validation pipelines with no defensive copying
/// required at the call site.
///
/// ## Error Collection
///
/// Errors are stored in a `Map<String, E>` keyed by field name. Use [#result()]
/// to obtain a `Result` wrapping the validated target or the error map, or
/// [#resultOr(Function)] to transform the error map into a custom type.
///
/// @param <T> the type of object being validated
/// @param <E> the error type produced by failed validations
/// @see Result
/// @author Jose
/// @since 1.0.0
public final class Validator<T, E> {

	private final T target;
	private final Map<String, E> errors;

	/// Private constructor. Use [#of(Object)] to create instances.
  ///
  /// @param target the object being validated
  /// @param errors the accumulated errors — ownership is transferred to this instance
	private Validator(T target, Map<String, E> errors) {
		this.target = target;
		this.errors = errors;
	}

	/// Creates a new, empty validator for the given target.
  ///
  /// This is the **entry point** for all validation operations.
  ///
  /// ```java
	/// var validator = Validator.<User, MyError>of(user);
	/// ```
  ///
  /// @param target the object to validate
  /// @param <T>    the target type
  /// @param <E>    the error type
  /// @return a new, empty validator for the target
	@Contract("_ -> new")
	public static <T, E> @NonNull Validator<T, E> of(T target) {
		return new Validator<>(target, new HashMap<>());
	}

	/// Returns a new validator with the given field-error pair added to the error map.
  ///
  /// This is the single mutation point for the entire class. All `validate*` methods
  /// delegate here, guaranteeing that every state transition produces a fresh instance.
  ///
  /// @param field the field name to associate the error with
  /// @param error the error value
  /// @return a new validator with the error appended
	@Contract("_, _ -> new")
	private @NonNull Validator<T, E> withError(@NonNull String field, E error) {
		final var next = new HashMap<>(this.errors);
		next.put(field, error);
		return new Validator<>(this.target, next);
	}

	/// Adds a validation rule with a **lazy** error supplier.
  ///
  /// The error is only computed when the predicate fails. Prefer this overload
  /// when error construction is expensive or has side effects.
  ///
  /// ```java
	/// validator.validate(
	///     u -> u.age() >= 18,
	///     "age",
	///     () -> new ValidationError("AGE_MIN", "Must be 18+", Severity.ERROR)
	/// );
	/// ```
  ///
  /// @param condition      the predicate to test against the target
  /// @param field          the field name for error reporting
  /// @param errorSupplier  supplier invoked only when validation fails
  /// @return `this` if the predicate passes, otherwise a new validator with the error recorded
  /// @throws NullPointerException if any parameter is null
	@Contract("_, _, _ -> new")
	public @NonNull Validator<T, E> validate(@NonNull Predicate<T> condition, @NonNull String field,
			@NonNull Supplier<E> errorSupplier) {
		return condition.test(this.target) ? this : this.withError(field, errorSupplier.get());
	}

	/// Adds a validation rule with an **eager** error value.
  ///
  /// The error is evaluated before the predicate is tested. Use
  /// [#validate(Predicate, String, Supplier)] instead when error construction
  /// is expensive.
  ///
  /// ```java
	/// validator.validate(
	///     u -> u.age() >= 18,
	///     "age",
	///     new ValidationError("AGE_MIN", "Must be 18+", Severity.ERROR)
	/// );
	/// ```
  ///
  /// @param condition the predicate to test against the target
  /// @param field     the field name for error reporting
  /// @param error     the error to record if validation fails
  /// @return `this` if the predicate passes, otherwise a new validator with the error recorded
  /// @throws NullPointerException if `condition` or `field` is null
	@Contract("_, _, _ -> new")
	public @NonNull Validator<T, E> validate(@NonNull Predicate<T> condition, @NonNull String field, E error) {
		return condition.test(this.target) ? this : this.withError(field, error);
	}

	/// Conditionally applies a block of validations.
  ///
  /// The `validation` function is only invoked when `condition` returns `true`,
  /// enabling context-dependent rules without breaking the fluent chain.
  ///
  /// ```java
	/// validator.validateIf(
	///     u -> u.type() == UserType.PREMIUM,
	///     v -> v.validate(u -> u.subscription() != null, "subscription", MISSING_SUB)
	/// );
	/// ```
  ///
  /// @param condition  the guard predicate
  /// @param validation the validation block applied when the condition holds
  /// @return the result of `validation` if the condition is true, otherwise `this`
  /// @throws NullPointerException if any parameter is null
	@Contract("_, _ -> new")
	public @NonNull Validator<T, E> validateIf(@NonNull Predicate<T> condition,
			@NonNull UnaryOperator<Validator<T, E>> validation) {
		return condition.test(this.target) ? validation.apply(this) : this;
	}

	/// Validates that a field extracted from the target is non-null.
  ///
  /// ```java
	/// validator.nonNull(User::email, "email", MISSING_EMAIL);
	/// ```
  ///
  /// @param extractor extracts the field value from the target
  /// @param field     the field name for error reporting
  /// @param error     the error to record if the field is null
  /// @param <U>       the field type
  /// @return `this` if the field is non-null, otherwise a new validator with the error recorded
  /// @throws NullPointerException if `extractor` or `field` is null
	@Contract("_, _, _ -> new")
	public <U> @NonNull Validator<T, E> nonNull(@NonNull Function<T, U> extractor, @NonNull String field, E error) {
		return this.validate(t -> extractor.apply(t) != null, field, error);
	}

	/// Validates that a string field matches a regex pattern.
  ///
  /// The field is also required to be non-null; a null value is treated as a failure.
  ///
  /// ```java
	/// validator.matches(
	///     User::email,
	///     "^[A-Za-z0-9+_.-]+@(.+)$",
	///     "email",
	///     INVALID_EMAIL_FORMAT
	/// );
	/// ```
  ///
  /// @param extractor extracts the string field from the target
  /// @param pattern   the regex pattern to test
  /// @param field     the field name for error reporting
  /// @param error     the error to record if validation fails
  /// @return `this` if the pattern matches, otherwise a new validator with the error recorded
  /// @throws NullPointerException if any parameter is null
	@Contract("_, _, _, _ -> new")
	public @NonNull Validator<T, E> matches(@NonNull Function<T, String> extractor, @NonNull String pattern,
			@NonNull String field, E error) {
		return this.validate(t -> {
			final var value = extractor.apply(t);
			return value != null && value.matches(pattern);
		}, field, error);
	}

	/// Validates that a numeric field falls within an inclusive range.
  ///
  /// A null value is treated as a failure.
  ///
  /// ```java
	/// validator.range(User::age, 0, 150, "age", INVALID_AGE);
	/// ```
  ///
  /// @param extractor extracts the numeric field from the target
  /// @param min       the minimum value (inclusive)
  /// @param max       the maximum value (inclusive)
  /// @param field     the field name for error reporting
  /// @param error     the error to record if validation fails
  /// @param <N>       a numeric type extending [Number]
  /// @return `this` if the value is in range, otherwise a new validator with the error recorded
  /// @throws NullPointerException if any parameter is null
	@Contract("_, _, _, _, _ -> new")
	public <N extends Number> @NonNull Validator<T, E> range(@NonNull Function<T, N> extractor, double min, double max,
			@NonNull String field, E error) {
		return this.validate(t -> {
			final var value = extractor.apply(t);
			if (value == null)
				return false;
			final double d = value.doubleValue();
			return d >= min && d <= max;
		}, field, error);
	}

	/// Validates that a string field's length falls within inclusive bounds.
  ///
  /// A null value is treated as a failure.
  ///
  /// ```java
	/// validator.length(User::username, 3, 20, "username", USERNAME_LENGTH);
	/// ```
  ///
  /// @param extractor extracts the string field from the target
  /// @param min       the minimum length (inclusive)
  /// @param max       the maximum length (inclusive)
  /// @param field     the field name for error reporting
  /// @param error     the error to record if validation fails
  /// @return `this` if the length is in bounds, otherwise a new validator with the error recorded
  /// @throws NullPointerException if any parameter is null
	@Contract("_, _, _, _, _ -> new")
	public @NonNull Validator<T, E> length(@NonNull Function<T, String> extractor, int min, int max,
			@NonNull String field, E error) {
		return this.validate(t -> {
			final var value = extractor.apply(t);
			if (value == null)
				return false;
			final int len = value.length();
			return len >= min && len <= max;
		}, field, error);
	}

	/// Returns the validation result as a [Result].
  ///
  /// - **No errors** → `Result.success(target)`
  /// - **Errors present** → `Result.failure(errors)` with an **unmodifiable** `Map<String, E>`
  ///
  /// ```java
	/// Result<User, Map<String, ValidationError>> result = validator.result();
	/// ```
  ///
  /// @return a `Result` containing either the valid target or the accumulated error map
	@Contract(" -> new")
	public @NonNull Result<T, Map<String, E>> result() {
		return this.errors.isEmpty() ? Result.success(this.target) : Result.failure(Map.copyOf(this.errors));
	}

	/// Returns the validation result with the error map transformed by a custom mapper.
  ///
  /// Use this when you want to collapse the error map into a single object
  /// or a flat list before handing it off to callers.
  ///
  /// ```java
	/// Result<User, ValidationErrors> result = validator.resultOr(
	///     errors -> new ValidationErrors(errors.values())
	/// );
	/// ```
  ///
  /// @param errorMapper transforms the `Map<String, E>` into a custom error type
  /// @param <F>         the final error type
  /// @return a `Result` wrapping either the valid target or the mapped error
  /// @throws NullPointerException if `errorMapper` is null
	@Contract("_ -> new")
	public <F> @NonNull Result<T, F> resultOr(@NonNull Function<Map<String, E>, F> errorMapper) {
		return this.errors.isEmpty()
				? Result.success(this.target)
				: Result.failure(errorMapper.apply(Map.copyOf(this.errors)));
	}

	/// Returns `true` if at least one validation has failed.
  ///
  /// @return `true` when the error map is non-empty
	@Contract(pure = true)
	public boolean hasErrors() {
		return !this.errors.isEmpty();
	}

	/// Returns the number of accumulated validation errors.
  ///
  /// @return the error count
	@Contract(pure = true)
	public int errorCount() {
		return this.errors.size();
	}

	/// Returns an unmodifiable view of the current error map.
  ///
  /// @return the error map; never null, may be empty
	@Contract(pure = true)
	public @NonNull Map<String, E> errors() {
		return Collections.unmodifiableMap(this.errors);
	}

	/// Applies a sequence of validation functions to a target in a single expression.
  ///
  /// Equivalent to chaining calls manually, but useful when the validations are
  /// defined elsewhere or passed as arguments.
  ///
  /// ```java
	/// Validator.compose(user,
	///     v -> v.nonNull(User::email, "email", MISSING),
	///     v -> v.length(User::username, 3, 20, "username", LENGTH),
	///     v -> v.range(User::age, 0, 150, "age", INVALID_AGE)
	/// ).result();
	/// ```
  ///
  /// @param target      the object to validate
  /// @param validations the validation functions applied in order
  /// @param <T>         the target type
  /// @param <E>         the error type
  /// @return the validator after all validations have been applied
  /// @throws NullPointerException if `target` or `validations` is null
	@SafeVarargs
	public static <T, E> @NonNull Validator<T, E> compose(T target,
			UnaryOperator<Validator<T, E>> @NonNull... validations) {
		return Arrays.stream(validations).reduce(of(target), (v, fn) -> fn.apply(v), (a, b) -> b);
	}
}
