package dev.jose.result;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
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
/// *   [Result.Success]: Contains the computed value.
/// *   [Result.Failure]: Contains a domain error.
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
///     .peek(email -> log.info("Found: " + email))
///     .flatMap(this::validateEmail)
///     .unwrapOrThrow();
/// ```
///
/// @param <T> The type of the value in case of success.
/// @param <E> The type of the error in case of failure (usually a Sealed Interface).
public sealed interface Result<T, E> {

	/// Creates a successful Result containing the given value.
	///
	/// @param value The value to wrap.
	/// @return A `Success` instance.
	@Contract("_ -> new")
	static <T, E> @NonNull Result<T, E> success(T value) {
		return new Success<>(value);
	}

	/// Creates a failed Result containing the given error.
	///
	/// @param error The error to wrap.
	/// @return A `Failure` instance.
	@Contract("_ -> new")
	static <T, E> @NonNull Result<T, E> failure(E error) {
		return new Failure<>(error);
	}

	/// Creates a Success if value is not null, otherwise returns a Failure.
	///
	/// @param value The potentially null value.
	/// @param errorSupplier Supplier for the error if value is null.
	/// @return A new Result.
	static <T, E> Result<T, E> ofNullable(@Nullable T value, Supplier<E> errorSupplier) {
		return value != null ? success(value) : failure(errorSupplier.get());
	}

	/// A helper for void operations that succeeded.
	///
	/// @return A Success containing null.
	@Contract(" -> new")
	static <E> @NonNull Result<Void, E> empty() {
		return success(null);
	}

	/// Executes a code block that might throw Checked Exceptions and captures them as a Result.
	///
	/// # Usage with ErrorRouter
	/// ```java
	/// Result.attempt(
	///     () -> repository.save(entity),
	///     UserError.FROM_EXCEPTION // Your declarative router
	/// );
	/// ```
	///
	/// @param action The generic supplier that might throw an Exception.
	/// @param errorMapper A function that converts the thrown Exception into domain error `E`.
	/// @return A Success containing the result, or a Failure containing the mapped error.
	static <T, E> Result<T, E> attempt(ThrowingSupplier<T> action, Function<Exception, E> errorMapper) {
		try {
			return success(action.get());
		} catch (final Exception e) {
			return failure(errorMapper.apply(e));
		}
	}

	/// Converts an Optional to a Result.
	/// @param optional The Optional to convert.
	/// @param errorSupplier Supplier for the error if the Optional is empty.
	/// @return A Success if the Optional has a value, otherwise a Failure.
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	static <T, E> Result<T, E> fromOptional(@NonNull Optional<T> optional, Supplier<E> errorSupplier) {
		return optional.<Result<T, E>>map(Result::success).orElseGet(() -> failure(errorSupplier.get()));
	}

	/// # Example
	/// ```java
	/// Stream<Result<User, Err>> results = userIds.stream().map(repo::findById);
	/// Result<List<User>, Err> allUsers = Result.collect(results);
	/// ```
	///
	/// @param results Stream of Result instances.
	/// @return Success with list of all values, or first Failure encountered.
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
				case Success(var val) -> accAction.accept(accumulator, val);
				case Failure(var err) -> {
					return failure(err);
				}
			}
		}

		return success(collector.finisher().apply(accumulator));
	}

	/// Convenience overload that collects into a List.
	static <T, E> Result<List<T>, E> collect(Stream<? extends Result<T, E>> results) {
		return collect(results, Collectors.toList());
	}

	/// Transforms a List of Results into a Result of List.
	///
	/// If all Results are Success, returns Success with the list of values.
	/// If any Result is Failure, returns the first Failure encountered.
	///
	/// This is the inverse of collect() — use sequence() when you have
	/// List<Result<T,E>>, use collect() when you have Stream<Result<T,E>>.
	///
	/// # Example
	/// ```java
	/// List<Result<Integer, String>> results = List.of(
	///     Result.success(1),
	///     Result.success(2),
	///     Result.success(3)
	/// );
	/// Result<List<Integer>, String> sequenced = Result.sequence(results);
	/// // Success([1, 2, 3])
	///
	/// List<Result<Integer, String>> withFailure = List.of(
	///     Result.success(1),
	///     Result.failure("oops"),
	///     Result.success(3)
	/// );
	/// Result<List<Integer>, String> failed = Result.sequence(withFailure);
	/// // Failure("oops")
	/// ```
	///
	/// @param results The list of Results to sequence.
	/// @return A Result containing all values or the first error.
	static <T, E> Result<List<T>, E> sequence(@NonNull List<Result<T, E>> results) {
		final List<T> values = new ArrayList<>(results.size());

		for (final Result<T, E> result : results) {
			switch (result) {
				case Success(var val) -> values.add(val);
				case Failure(var err) -> {
					return failure(err);
				}
			}
		}

		return success(values);
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
			case Success(var inner) -> inner;
			case Failure(var err) -> failure(err);
		};
	}

	/// Transforms the value if this is a [Success], otherwise passes the [Failure] through.
	///
	/// # Example
	/// ```java
	/// Result<Integer, Err> res = Result.success("10");
	/// Result<Integer, Err> mapped = res.map(Integer::parseInt);
	/// ```
	///
	/// @param mapper A function to apply to the value.
	/// @param <U> The new type of the value.
	/// @return A new Result containing the transformed value or the original error.
	default <U> Result<U, E> map(Function<T, U> mapper) {
		return switch (this) {
			case Success(var val) -> success(mapper.apply(val));
			case Failure(var err) -> failure(err);
		};
	}

	/// Combines this Result with another independent Result.
	///
	/// If both are Success, the combiner function is executed. If either is [Failure], the first
	/// [Failure] encountered is returned.
	/// Transforms the error if this is a [Failure], otherwise passes the [Success] through.
	///
	/// Use this to translate error types between layers (e.g., Repository -> Service).
	///
	/// # Example
	/// ```java
	/// Result<User, ApiError> response = repositoryResult
	///     .mapError(dbErr -> new ApiError("Database failure", dbErr.code()));
	/// ```
	///
	/// @param mapper A function to apply to the error.
	/// @param <F> The new type of the error.
	/// @return A new Result containing the original value or the transformed error.
	default <F> Result<T, F> mapError(Function<E, F> mapper) {
		return switch (this) {
			case Success(var val) -> success(val);
			case Failure(var err) -> failure(mapper.apply(err));
		};
	}

	/// Maps both [Success] and [Failure] in a single operation.
	///
	/// Useful when you need to transform both paths of the Result simultaneously.
	///
	/// # Example
	/// ```java
	/// Result<String, String> result = original.mapBoth(
	///     user -> user.email(),
	///     error -> error.message()
	/// );
	/// ```
	///
	/// @param successMapper Function to transform the success value.
	/// @param failureMapper Function to transform the failure error.
	/// @return A new Result with both types potentially transformed.
	default <U, F> Result<U, F> mapBoth(Function<T, U> successMapper, Function<E, F> failureMapper) {
		return switch (this) {
			case Success(var val) -> success(successMapper.apply(val));
			case Failure(var err) -> failure(failureMapper.apply(err));
		};
	}

	/// Applies a predicate to the value if this is a [Success].
	///
	/// If the predicate returns false, the Result is converted to a [Failure] using the provided
	/// supplier.
	/// Chains a function that itself returns a Result.
	///
	/// Use this when your transformation logic might also fail.
	///
	/// # Example
	/// ```java
	/// Result<User, Err> user = repo.find(id);
	/// // validate() returns Result<User, Err>
	/// Result<User, Err> validated = user.flatMap(u -> service.validate(u));
	/// ```
	///
	/// @param mapper A function that returns a Result.
	/// @param <U> The new type of the value.
	/// @return The result of the mapper, or the original failure.
	default <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
		return switch (this) {
			case Success(var val) -> mapper.apply(val);
			case Failure(var err) -> failure(err);
		};
	}

	/// @param other The other Result to combine with.
	/// @param combiner BiFunction to merge the two values.
	/// @param <U> The type of the other value.
	/// @param <V> The type of the combined value.
	/// @return A new Result containing the combined value or the failure.
	default <U, V> Result<V, E> combine(Result<U, E> other, BiFunction<T, U, V> combiner) {
		return switch (this) {
			case Failure(var err) -> failure(err); // Fail fast
			case Success(var t) -> switch (other) {
				case Failure(var err) -> failure(err);
				case Success(var u) -> success(combiner.apply(t, u));
			};
		};
	}

	/// Attempts to recover from a failure with a function that returns another [Result].
	///
	/// Use this when your recovery logic itself might fail (e.g., fallback to cache, which might also
	/// fail).
	/// Collapses the result into a single value by handling both success and failure cases.
	///
	/// This is the preferred way to exit the Result monad, forcing handling of both paths.
	///
	/// # Example
	/// ```java
	/// String response = result.fold(
	///     user -> "Found: " + user.name(),
	///     error -> "Error: " + error.message()
	/// );
	/// ```
	///
	/// @param onSuccess The function to apply if this is a Success.
	/// @param onFailure The function to apply if this is a Failure.
	/// @param <R> The type of the resulting value.
	/// @return The result of applying the appropriate function.
	default <R> R fold(Function<T, R> onSuccess, Function<E, R> onFailure) {
		return switch (this) {
			case Success(var val) -> onSuccess.apply(val);
			case Failure(var err) -> onFailure.apply(err);
		};
	}

	/// # Example
	/// ```java
	/// Result<User, Err> validUser = userResult
	///     .filter(User::isActive, () -> new Err("User is inactive"));
	/// ```
	///
	/// @param predicate The condition to test the value against.
	/// @param errorSupplier A supplier for the error if the predicate fails.
	/// @return The original Success if the predicate matches, otherwise a new Failure.
	default Result<T, E> filter(Predicate<T> predicate, Supplier<E> errorSupplier) {
		return switch (this) {
			case Success(var val) -> predicate.test(val) ? this : failure(errorSupplier.get());
			case Failure(_) -> this;
		};
	}

	/// Collects a stream of Results into a Result of R using a custom Collector. Short-circuits on
	/// the first Failure encountered.
	/// Attempts to recover from a [Failure] by applying a function to the error.
	///
	/// # Example
	/// ```java
	/// Result<User, Err> recovered = result
	///     .recover(err -> User.guest());
	/// ```
	///
	/// @param recoveryFunction A function that produces a success value from an error.
	/// @return A Success containing either the original value or the recovered value.
	default Result<T, E> recover(Function<E, T> recoveryFunction) {
		return switch (this) {
			case Success(_) -> this;
			case Failure(var err) -> success(recoveryFunction.apply(err));
		};
	}

	///
	/// @param recoveryMapper Function that takes the error and returns a new Result.
	/// @return The original Success, or the result of the recovery attempt.
	default Result<T, E> recoverWith(Function<E, Result<T, E>> recoveryMapper) {
		return switch (this) {
			case Success(_) -> this;
			case Failure(var err) -> recoveryMapper.apply(err);
		};
	}

	/// Executes a side effect if this is a [Success].
	///
	/// Useful for logging or metrics without modifying the stream.
	///
	/// @param action The consumer to execute.
	/// @return `this` instance (fluent API).
	default Result<T, E> peek(Consumer<T> action) {
		if (this instanceof Success(var val)) {
			action.accept(val);
		}
		return this;
	}

	/// Executes a side effect if this is a [Failure].
	///
	/// Useful for logging errors without modifying the result or breaking the chain.
	///
	/// @param action The consumer to execute with the error.
	/// @return `this` instance (fluent API).
	default Result<T, E> peekFailure(Consumer<E> action) {
		if (this instanceof Failure(var err)) {
			action.accept(err);
		}
		return this;
	}

	/// Returns true if this is a Success.
  /// @return `true` if instance of [Success], `false` otherwise
	default boolean isSuccess() {
		return this instanceof Success;
	}

	/// Returns true if this is a Failure.
  /// @return `true` if instance of [Failure], `false` otherwise
	default boolean isFailure() {
		return this instanceof Failure;
	}

	/// Unsafely extracts the value or throws a RuntimeException.
	///
	/// # ⚠️ Warning
	/// This method defeats the purpose of the Result pattern. Use it **only** in unit tests or when
	/// you are 100% sure the failure case is
	/// impossible. Otherwise, prefer safe handling or [#unwrapOrThrow(Function)].
	///
	/// @return The success value.
	/// @throws RuntimeException if this is a Failure.
	default T unwrap() {
		return switch (this) {
			case Success(var val) -> val;
			case Failure(var err) -> throw new RuntimeException("Unwrapped a Failure: " + err);
		};
	}

	/// Unsafely extracts the value or throws a mapped [RuntimeException].
	///
	/// # Usage Example
	/// ```java
	/// User user = result.unwrapOrThrow(err -> new NotFoundException(err.message()));
	/// ```
	///
	/// @param exceptionMapper A function that maps the error `E` to a RuntimeException.
	/// @return The success value.
	/// @throws RuntimeException as mapped by the provided function.
	default T unwrapOrThrow(Function<? super E, ? extends RuntimeException> exceptionMapper) {
		return switch (this) {
			case Success(var val) -> val;
			case Failure(var err) -> throw exceptionMapper.apply(err);
		};
	}

	/// Safely extracts the value or returns a default.
	///
	/// # Usage Example
	/// ```java
	/// User user = result.unwrapOr(defaultUser);
	/// ```
	///
	/// @param defaultValue The default value to return in case of Failure.
	/// @return The success value or the default.
	default T unwrapOr(T defaultValue) {
		return switch (this) {
			case Success(var val) -> val;
			case Failure(_) -> defaultValue;
		};
	}

	/// Safely extracts the value or computes a default.
	///
	/// # Usage Example
	/// ```java
	/// User user = result.unwrapOrElse(() -> fetchDefaultUser());
	/// ```
	///
	/// @param defaultSupplier A supplier that provides the default value in case of Failure.
	/// @return The success value or the computed default.
	default T unwrapOrElse(Supplier<T> defaultSupplier) {
		return switch (this) {
			case Success(var val) -> val;
			case Failure(_) -> defaultSupplier.get();
		};
	}

	/// Converts this Result into a Stream.
	///
	/// @return A [Stream] containing the value if [Success]`, or an empty [Stream] if Failure.
	default Stream<T> stream() {
		return switch (this) {
			case Success(var val) -> Stream.of(val);
			case Failure(_) -> Stream.empty();
		};
	}

	/// Converts this Result into an Optional.
	///
	/// @return An [Optional] containing the value if Success, or empty if Failure.
	default Optional<T> toOptional() {
		return switch (this) {
			case Success(var val) -> Optional.ofNullable(val);
			case Failure(_) -> Optional.empty();
		};
	}

	/// A functional interface for suppliers that can throw checked exceptions.
	@FunctionalInterface
	interface ThrowingSupplier<T> {
		T get() throws Exception;
	}

	/// Represents a successful operation containing a value.
	record Success<T, E>(T value) implements Result<T, E> {
	}

	/// Represents a failed operation containing a domain error.
	record Failure<T, E>(E error) implements Result<T, E> {
	}
}
