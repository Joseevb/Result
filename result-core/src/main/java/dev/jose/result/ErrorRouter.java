package dev.jose.result;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/// A **pure, immutable** error router that maps Java exceptions to domain-specific error types.
///
/// This class provides a functional approach to exception mapping, allowing you to declaratively
/// define how different exception types should be transformed into your application's error
/// representation. The router is **immutable** — all configuration methods return new instances.
///
/// ## Design Philosophy
///
/// - **Pure**: No side effects, no external dependencies
/// - **Immutable**: Thread-safe by design, no synchronization needed
/// - **Composable**: Chain methods to build complex routing rules
/// - **Type-safe**: Leverages Java's type system for compile-time safety
///
/// ## Basic Usage
///
/// ```java
/// var router = ErrorRouter
///     .<AppError>defaultsTo(ex -> AppError.UNKNOWN)
///     .map(IllegalArgumentException.class, ex -> AppError.INVALID_INPUT)
///     .map(IOException.class, ex -> AppError.NETWORK_FAILURE);
///
/// AppError error = router.apply(someException);
/// ```
///
/// ## Rule Ordering
///
/// Rules are evaluated in **registration order**. More specific exception types should be
/// registered before general ones:
///
/// ```java
/// var router = ErrorRouter
///     .<AppError>defaultsTo(ex -> AppError.UNKNOWN)
///     .map(FileNotFoundException.class, ex -> AppError.MISSING_FILE)  // Specific first
///     .map(IOException.class, ex -> AppError.IO_ERROR);               // General second
/// ```
///
/// @param <E> the domain error type this router produces
/// @see MeteredErrorRouter for adding metrics
/// @see CachedErrorRouter for adding caching
/// @author Jose
/// @since 1.0.0
public final class ErrorRouter<E> implements Function<Exception, E> {

	private final List<Rule<E>> rules;
	private final Function<Exception, E> fallback;

	/// Internal record representing a single routing rule.
  ///
  /// Each rule associates an exception type with a mapper function that converts
  /// instances of that exception (or its subclasses) to the domain error type.
  ///
  /// @param <E> the domain error type
	private record Rule<E>(Class<? extends Exception> type, Function<Exception, E> mapper) {
		/// Checks if this rule matches the given exception.
    ///
    /// Uses `isInstance()` to support both exact matches and subclasses.
    ///
    /// @param ex the exception to check
    /// @return `true` if this rule applies to the exception
		boolean matches(Exception ex) {
			return this.type.isInstance(ex);
		}
	}

	/// Private constructor. Use factory methods instead.
  ///
  /// @param rules    the list of routing rules (already copied/defensive)
  /// @param fallback the fallback mapper for unmatched exceptions
	private ErrorRouter(List<Rule<E>> rules, Function<Exception, E> fallback) {
		this.rules = Objects.requireNonNull(rules, "Rules list cannot be null");
		this.fallback = Objects.requireNonNull(fallback, "Fallback ErrorRouter cannot be null");
	}

	/// Creates a new `ErrorRouter` with the specified fallback mapper.
  ///
  /// The fallback is invoked when no registered rule matches an exception.
  /// This is the **entry point** for building a router.
  ///
  /// ## Example
  ///
  /// ```java
    /// var router = ErrorRouter
    ///     .<ApiError>defaultsTo(ex -> ApiError.UNEXPECTED);
    /// ```
  ///
  /// @param fallback the function to apply when no rule matches
  /// @param <E>      the domain error type
  /// @return a new `ErrorRouter` with only the fallback configured
  /// @throws NullPointerException if `fallback` is null
	@Contract("_ -> new")
	public static <E> @NonNull ErrorRouter<E> defaultsTo(@NonNull Function<Exception, E> fallback) {
		return new ErrorRouter<>(List.of(), fallback);
	}

	/// Registers a mapping for a specific exception type.
  ///
  /// The mapper will be applied to instances of the specified type **and its subclasses**.
  /// To avoid shadowing issues, register more specific types before general ones.
  ///
  /// ## Example
  ///
  /// ```java
    /// var router = ErrorRouter
    ///     .<AppError>defaultsTo(ex -> AppError.UNKNOWN)
    ///     .map(ValidationException.class, ex -> AppError.VALIDATION_FAILED);
    /// ```
  ///
  /// @param type   the exception type to match (including subclasses)
  /// @param mapper the function to convert matching exceptions to domain errors
  /// @param <X>    the specific exception type
  /// @return a new `ErrorRouter` with this rule appended
  /// @throws NullPointerException if `type` or `mapper` is null
	@Contract("_, _ -> new")
	public <X extends Exception> @NonNull ErrorRouter<E> map(@NonNull Class<X> type, @NonNull Function<X, E> mapper) {
		final var newRules = new ArrayList<Rule<E>>(this.rules.size() + 1);
		newRules.addAll(this.rules);
		newRules.add(new Rule<>(type, ex -> mapper.apply(type.cast(ex))));
		return new ErrorRouter<>(List.copyOf(newRules), this.fallback);
	}

	/// Applies this router to an exception, returning the mapped domain error.
  ///
  /// Rules are evaluated in registration order. The first matching rule's
  /// mapper is applied. If no rule matches, the fallback mapper is used.
  ///
  /// ## Thread Safety
  ///
  /// This method is **thread-safe**. The router is immutable, so concurrent
  /// invocations do not interfere.
  ///
  /// @param exception the exception to map
  /// @return the domain error representation
  /// @throws NullPointerException if `exception` is null
	@Override
	public E apply(@NonNull Exception exception) {
		for (final var rule : this.rules) {
			if (rule.matches(exception)) {
				return rule.mapper().apply(exception);
			}
		}
		return this.fallback.apply(exception);
	}

	/// Returns the number of registered rules (excluding the fallback).
  ///
  /// @return the count of explicit mapping rules
	public int ruleCount() {
		return this.rules.size();
	}

	/// Checks if any rule is registered for the exact specified type.
  ///
  /// Note: This checks for exact type match, not subclass matching.
  /// Use `ruleCount()` to check if any rules exist at all.
  ///
  /// @param type the exception type to check
  /// @return `true` if a rule exists for the exact type
	public boolean hasRuleFor(@NonNull Class<? extends Exception> type) {
		return this.rules.stream().anyMatch(r -> r.type().equals(type));
	}
}
