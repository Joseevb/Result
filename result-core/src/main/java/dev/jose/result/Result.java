package dev.jose.result;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// A generic implementation of the **Result Pattern** (Monad).
///
/// This interface represents a value that can be one of two states:
/// *   [Result.Ok]: Contains the computed value.
/// *   [Result.Err]: Contains a domain error.
///
/// It promotes **Railway Oriented Programming**, allowing you to chain operations without throwing
/// exceptions.
///
/// # Usage Example
/// ```java
/// Result<User, UserError> result = userService.findById(id);
///
/// var response = result
///     .map(User::getEmail)
///     .inspect(email -> log.info("Found: " + email))
///     .andThen(this::validateEmail)
///     .unwrapOrThrow();
/// ```
///
/// @param <T> The type of the value in case of `Ok`.
/// @param <E> The domain error type in case of `Err`, commonly a sealed error hierarchy.
///
/// Neither variant can contain `null`. Use [#empty()] for a successful operation that has no
/// meaningful value; it contains [Unit#INSTANCE].
public sealed interface Result<T, E> {

	/// Creates an `Ok` containing the given value.
	///
	/// @param value The value to wrap.
	/// @return An `Ok` instance.
	static <T, E> @NonNull Result<T, E> ok(T value) {
		return new Ok<>(value);
	}

	/// Creates an `Err` containing the given error.
	///
	/// @param error The error to wrap.
	/// @return An `Err` instance.
	static <T, E> @NonNull Result<T, E> err(E error) {
		return new Err<>(error);
	}

	/// Creates an `Ok` if value is not null, otherwise returns an `Err`.
	///
	/// @param value The potentially null value.
	/// @param errorSupplier Supplier for the error if value is null.
	/// @return A new Result.
    static <T, E> Result<T, E> ofNullable(@Nullable T value, Supplier<? extends E> errorSupplier) {
		return value != null ? ok(value) : err(errorSupplier.get());
	}

	/// A helper for void operations that succeeded.
	///
	/// @return An `Ok` containing [Unit], practically empty.
	static <E> @NonNull Result<Unit, E> empty() {
		return ok(Unit.INSTANCE);
	}

    /// Executes a supplier and captures thrown [Exception] instances as `Err`.
    ///
    /// `Error` instances are deliberately not caught. A supplier returning `null` also fails
    /// immediately because `Ok` values are non-null.
    ///
    /// @param action The supplier that may throw an exception.
    /// @param <T> The supplied value type.
    /// @return `Result<T, Exception>` containing the supplied value or exception.
    static <T> Result<T, Exception> from(ThrowingSupplier<? extends T> action) {
        final T value;
        try {
            value = action.get();
        } catch (final Exception e) {
            return err(e);
        }
        return ok(value);
    }

	/// Creates a Result from a throwing supplier, converting caught exceptions to a typed error.
    ///
    /// This is the typed-error conversion path. Only [Exception] instances are caught; `Error`
    /// instances propagate. A `null` supplier value or mapped error fails immediately.
    ///
    /// @param action The supplier that may throw an exception.
    /// @param errorMapper Converts a caught exception into the domain error type.
    /// @param <T> The supplied value type.
    /// @param <E> The domain error type.
    /// @return `Ok` with the supplied value, or `Err` with the mapped exception.
	static <T, E> Result<T, E> from(ThrowingSupplier<? extends T> action,
            Function<? super Exception, ? extends E> errorMapper) {
        final T value;
        try {
            value = action.get();
        } catch (final Exception e) {
            return err(errorMapper.apply(e));
        }
        return ok(value);
    }

	/// Converts an Optional to a Result.
	/// @param optional The Optional to convert.
	/// @param errorSupplier Supplier for the error if the Optional is empty.
	/// @return An `Ok` if the Optional has a value, otherwise an `Err`.
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    static <T, E> Result<T, E> fromOptional(@NonNull Optional<? extends T> optional,
            Supplier<? extends E> errorSupplier) {
		return optional.<Result<T, E>>map(Result::ok).orElseGet(() -> err(errorSupplier.get()));
	}

	/// # Example
	/// ```java
    /// Stream<Result<User, UserError>> results = userIds.stream().map(repo::findById);
    /// Result<List<User>, UserError> allUsers = Result.collect(results);
	/// ```
	///
	/// @param results Stream of Result instances.
	/// @return Ok with list of all values, or first Err encountered.
	static <T, E, A, R> Result<R, E> collect(@NonNull Stream<? extends Result<T, E>> results,
			@NonNull Collector<? super T, A, R> collector) {
		final A accumulator = collector.supplier().get();
		final var accAction = collector.accumulator();

		// Use iterator to allow short-circuiting without loading the whole stream into
		// memory first
		final var iterator = results.iterator();
		while (iterator.hasNext()) {
			final Result<T, E> result = iterator.next();
			switch (result) {
				case Ok(var val) -> accAction.accept(accumulator, val);
				case Err(var err) -> {
					return err(err);
				}
			}
		}

		return ok(collector.finisher().apply(accumulator));
	}

	/// Convenience overload that collects into a List.
	///
	/// @param results the Results to collect
	/// @param <T> the value type
	/// @param <E> the error type
	/// @return an `Ok` containing all values, or the first `Err`
	static <T, E> Result<List<T>, E> collect(Stream<? extends Result<T, E>> results) {
		return collect(results, Collectors.toList());
	}

	/// Transforms a List of Results into a Result of List.
	///
	/// If all Results are Ok, returns Ok with the list of values.
	/// If any Result is Err, returns the first Err encountered.
	///
	/// This is the inverse of collect() — use sequence() when you have
	///
	/// # Example
	/// ```java
	/// List<Result<Integer, String>> results = List.of(
	///     Result.ok(1),
	///     Result.ok(2),
	///     Result.ok(3)
	/// );
	/// Result<List<Integer>, String> sequenced = Result.sequence(results);
	/// // Ok([1, 2, 3])
	///
	/// List<Result<Integer, String>> withFailure = List.of(
	///     Result.ok(1),
	///     Result.err("oops"),
	///     Result.ok(3)
	/// );
	/// Result<List<Integer>, String> failed = Result.sequence(withFailure);
	/// // Err("oops")
	/// ```
	///
	/// @param results The list of Results to sequence.
	/// @return A Result containing all values or the first error.
    static <T, E> Result<List<T>, E> sequence(@NonNull List<? extends Result<T, E>> results) {
		final List<T> values = new ArrayList<>(results.size());

		for (final Result<T, E> result : results) {
			switch (result) {
				case Ok(var val) -> values.add(val);
				case Err(var err) -> {
					return err(err);
				}
			}
		}

		return ok(values);
	}

	/// Convenience overload for arrays.
	///
	/// @param results Array of Results to sequence.
	/// @return A Result containing all values or the first error.
	@SafeVarargs
	static <T, E> Result<List<T>, E> sequence(Result<T, E> @NonNull... results) {
		return sequence(List.of(results));
	}

	/// Flattens a nested [Result] structure.
	///
	/// Converts `Result<Result<T, E>, E>` to `Result<T, E>`.
	///
	/// @param nested The nested Result to flatten.
	/// @return The flattened Result.
	static <T, E> Result<T, E> flatten(@NonNull Result<Result<T, E>, E> nested) {
		return switch (nested) {
			case Ok(var inner) -> inner;
			case Err(var err) -> err(err);
		};
	}

	/// Transforms the value if this is a [Ok], otherwise passes the [Err] through.
	///
	/// # Example
	/// ```java
    /// Result<String, ParseError> res = Result.ok("10");
    /// Result<Integer, ParseError> mapped = res.map(Integer::parseInt);
	/// ```
	///
	/// @param mapper A function to apply to the value.
	/// @param <U> The new type of the value.
	/// @return A new Result containing the transformed value or the original error.
    default <U> Result<U, E> map(Function<? super T, ? extends U> mapper) {
		return switch (this) {
			case Ok(var val) -> ok(mapper.apply(val));
			case Err(var err) -> err(err);
		};
	}

	/// Transforms the error if this is a [Err], otherwise passes the [Ok] through.
	///
	/// Use this to translate error types between layers (e.g., Repository -> Service).
	///
	/// # Example
	/// ```java
	/// Result<User, ApiError> response = repositoryResult
	///     .mapErr(dbErr -> new ApiError("Database failure", dbErr.code()));
	/// ```
	///
	/// @param mapper A function to apply to the error.
	/// @param <F> The new type of the error.
	/// @return A new Result containing the original value or the transformed error.
    default <F> Result<T, F> mapErr(Function<? super E, ? extends F> mapper) {
		return switch (this) {
			case Ok(var val) -> ok(val);
			case Err(var err) -> err(mapper.apply(err));
		};
	}

	/// Maps both [Ok] and [Err] in a single operation.
	///
	/// Useful when you need to transform both paths of the Result simultaneously.
	///
	/// # Example
	/// ```java
    /// Result<String, String> result = original.map(
	///     user -> user.email(),
	///     error -> error.toString()
	/// );
	/// ```
	///
	/// @param okMapper function to transform the `Ok` value
    /// @param errMapper function to transform the `Err` error
	/// @return A new Result with both types potentially transformed.
    default <U, F> Result<U, F> map(Function<? super T, ? extends U> okMapper,
            Function<? super E, ? extends F> errMapper) {
		return switch (this) {
			case Ok(var val) -> ok(okMapper.apply(val));
			case Err(var err) -> err(errMapper.apply(err));
		};
	}

	/// Chains a function that itself returns a Result.
	///
	/// Use this when your transformation logic might also fail.
	///
	/// # Example
	/// ```java
    /// Result<User, UserError> user = repo.find(id);
    /// // validate() returns Result<User, UserError>
    /// Result<User, UserError> validated = user.andThen(u -> service.validate(u));
	/// ```
	///
	/// @param mapper a function that returns a Result
	/// @param <U> the new type of the value
	/// @return the result of the mapper, or the original `Err`
    default <U> Result<U, E> andThen(Function<? super T, ? extends Result<U, E>> mapper) {
		return switch (this) {
				case Ok(var val) -> Objects.requireNonNull(mapper.apply(val),
						"andThen mapper returned null");
			case Err(var err) -> err(err);
		};
	}

	/// Combines this Result with another independent Result.
	///
	/// If both are `Ok`, the combiner runs. Otherwise, the first `Err` is returned.
	///
	/// @param other the other Result to combine with
	/// @param combiner function to merge the two `Ok` values
	/// @param <U> the type of the other value
	/// @param <V> the combined value type
	/// @return a new Result containing the combined value or an `Err`
    default <U, V> Result<V, E> combine(Result<U, E> other,
            BiFunction<? super T, ? super U, ? extends V> combiner) {
		return switch (this) {
			case Err(var err) -> err(err); // Fail fast
			case Ok(var t) -> switch (other) {
				case Err(var err) -> err(err);
				case Ok(var u) -> ok(combiner.apply(t, u));
			};
		};
	}

	/// Collapses the Result into one value by handling both `Ok` and `Err` cases.
	///
	/// # Example
	/// ```java
	/// String response = result.fold(
	///     user -> "Found: " + user.name(),
	///     error -> "Error: " + error.toString()
	/// );
	/// ```
	///
	/// @param onOk The function to apply if this is an `Ok`.
	/// @param onErr The function to apply if this is an `Err`.
	/// @param <R> The type of the resulting value.
	/// @return The result of applying the appropriate function.
	default <R> R fold(Function<T, R> onOk, Function<E, R> onErr) {
		return switch (this) {
			case Ok(var val) -> onOk.apply(val);
			case Err(var err) -> onErr.apply(err);
		};
	}

	/// Filters the `Ok` value with a predicate.
	///
	/// If the predicate returns false, the Result becomes an `Err` from the supplied error.
	/// Existing `Err` values pass through unchanged.
	///
	/// # Example
	/// ```java
    /// Result<User, UserError> validUser = userResult
    ///     .filter(User::isActive, () -> new UserError.Inactive("User is inactive"));
	/// ```
	///
	/// @param predicate The condition to test the value against.
	/// @param errorSupplier A supplier for the error if the predicate fails.
	/// @return The original Ok if the predicate matches, otherwise a new Err.
    default Result<T, E> filter(Predicate<? super T> predicate, Supplier<? extends E> errorSupplier) {
		return switch (this) {
			case Ok(var val) -> predicate.test(val) ? this : err(errorSupplier.get());
			case Err(_) -> this;
		};
	}

	/// Attempts to recover from a [Err] by applying a function to the error.
	///
	/// # Example
	/// ```java
    /// Result<User, UserError> recovered = result
	///     .recover(err -> User.guest());
	/// ```
	///
	/// @param recoveryFunction a function that produces an `Ok` value from an `Err`
	/// @return an `Ok` containing either the original value or the recovered value
    default Result<T, E> recover(Function<? super E, ? extends T> recoveryFunction) {
		return switch (this) {
			case Ok(_) -> this;
			case Err(var err) -> ok(recoveryFunction.apply(err));
		};
	}

    /// Recovers from an `Err` with another `Result`.
    ///
    /// `recoverWith` makes the callback's Result return type explicit and avoids the ambiguity of
    /// `Optional.orElse`, whose argument is a plain fallback value.
    ///
    /// @param recoveryMapper Function that takes the error and returns a new Result.
    /// @return The original `Ok`, or the recovered Result.
    default Result<T, E> recoverWith(Function<? super E, ? extends Result<T, E>> recoveryMapper) {
		return switch (this) {
			case Ok(_) -> this;
			case Err(var err) -> Objects.requireNonNull(recoveryMapper.apply(err),
					"recoverWith callback returned null");
		};
	}

	/// Executes a side effect if this is a [Ok].
	///
	/// Useful for logging or metrics without modifying the stream.
	///
	/// @param action The consumer to execute.
	/// @return `this` instance (fluent API).
    default Result<T, E> inspect(Consumer<? super T> action) {
		if (this instanceof Ok(var val)) {
			action.accept(val);
		}
		return this;
	}

	/// Executes a side effect if this is a [Err].
	///
	/// Useful for logging errors without modifying the result or breaking the chain.
	///
	/// @param action The consumer to execute with the error.
	/// @return `this` instance (fluent API).
    default Result<T, E> inspectErr(Consumer<? super E> action) {
		if (this instanceof Err(var err)) {
			action.accept(err);
		}
		return this;
	}

	/// Returns true if this is an `Ok`.
  /// @return `true` if instance of [Ok], `false` otherwise
	default boolean isOk() {
		return this instanceof Ok;
	}

	/// Returns true if this is an `Err`.
  /// @return `true` if instance of [Err], `false` otherwise
	default boolean isErr() {
		return this instanceof Err;
	}

	/// Unsafely extracts the value or throws a RuntimeException.
	///
	/// # ⚠️ Warning
	/// This method defeats the purpose of the Result pattern. Use it **only** in unit tests or when
	/// you are 100% sure the `Err` case is
	/// impossible. Otherwise, prefer safe handling or [#unwrapOrThrow(Function)].
	///
	/// @return The `Ok` value.
	/// @throws RuntimeException if this is an `Err`.
	default T unwrap() {
		return switch (this) {
			case Ok(var val) -> val;
			case Err(var err) -> throw new RuntimeException("Unwrapped an Err: " + err);
		};
	}

	/// Unsafely extracts the value or throws a mapped [RuntimeException].
	///
	/// # Usage Example
	/// ```java
	/// User user = result.unwrapOrThrow(err -> new NotFoundException(err.toString()));
	/// ```
	///
	/// @param exceptionMapper A function that maps the error `E` to a RuntimeException.
	/// @return The `Ok` value.
	/// @throws RuntimeException as mapped by the provided function.
	default T unwrapOrThrow(Function<? super E, ? extends RuntimeException> exceptionMapper) {
		return switch (this) {
			case Ok(var val) -> val;
			case Err(var err) -> throw exceptionMapper.apply(err);
		};
	}

	/// Safely extracts the value or returns a default.
	///
	/// # Usage Example
	/// ```java
	/// User user = result.unwrapOr(defaultUser);
	/// ```
	///
	/// @param defaultValue The default value to return in case of Err.
	/// @return The `Ok` value or the default.
	default T unwrapOr(T defaultValue) {
		return switch (this) {
			case Ok(var val) -> val;
			case Err(_) -> defaultValue;
		};
	}

	/// Safely extracts the value or computes a default.
	///
	/// # Usage Example
	/// ```java
	/// User user = result.unwrapOrElse(() -> fetchDefaultUser());
	/// ```
	///
	/// @param defaultSupplier A supplier that provides the default value in case of Err.
	/// @return The `Ok` value or the computed default.
    default T unwrapOrElse(Supplier<? extends T> defaultSupplier) {
		return switch (this) {
			case Ok(var val) -> val;
			case Err(_) -> defaultSupplier.get();
		};
	}

	/// Converts this Result into a Stream.
	///
	/// @return A [Stream] containing the value if [Ok]`, or an empty [Stream] if Err.
	default Stream<T> stream() {
		return switch (this) {
			case Ok(var val) -> Stream.of(val);
			case Err(_) -> Stream.empty();
		};
	}

	/// Converts this Result into an Optional.
	///
	/// @return An [Optional] containing the value if Ok, or empty if Err.
	default Optional<T> toOptional() {
		return switch (this) {
			case Ok(var val) -> Optional.of(val);
			case Err(_) -> Optional.empty();
		};
	}

	/// A functional interface for suppliers that can throw checked exceptions.
	@FunctionalInterface
	interface ThrowingSupplier<T> {
		T get() throws Exception;
	}

	/// Represents an `Ok` containing a non-null value.
	record Ok<T, E>(@NonNull T value) implements Result<T, E> {
		public Ok {
			Objects.requireNonNull(value, "Ok value cannot be null");
		}
	}

	/// Represents a failed operation containing a domain error.
	record Err<T, E>(@NonNull E error) implements Result<T, E> {
		public Err {
			Objects.requireNonNull(error, "Err error cannot be null");
		}
	}

	/// Unit value used by [#empty()] for successful operations without a result value.
	enum Unit {
		/// The single Unit value.
		INSTANCE
	}
}
