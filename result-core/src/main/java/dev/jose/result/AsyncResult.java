package dev.jose.result;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/// A monadic wrapper for asynchronous computations that produce a [Result].
///
/// `AsyncResult` represents a computation that will eventually complete with either
/// a success value or a domain error. It provides railway-oriented programming
/// semantics for async operations, allowing you to chain transformations without
/// nested callback hell.
///
/// Unlike [Result], which is a sealed interface discriminating between Success and
/// Failure, `AsyncResult` is a sealed interface representing "eventually a Result".
/// The success/failure distinction is deferred until the underlying future completes.
///
/// # Key Characteristics
/// - Lazy: transformations build a computation graph without executing
/// - Short-circuiting: first Failure propagates immediately, cancelling pending work
/// - Composable: chain with [this.map], [this.flatMap], [this.combine]
/// - Collectible: aggregate multiple async results with [this.collectAll]
///
/// # Creation
/// ```java
/// // From an existing CompletableFuture
/// AsyncResult<User, UserError> userAsync = AsyncResult.attempt(
///     webClient.getUser(id),           // CompletableFuture<User>
///     ex -> new UserError.Network(ex)  // error mapper
/// );
///
/// // From a completed Result (synchronous fallback)
/// AsyncResult<User, UserError> cached = AsyncResult.completed(Result.success(cachedUser));
/// ```
///
/// # Chaining
/// ```java
/// AsyncResult<Order, OrderError> orderAsync = userAsync
///     .flatMap(user -> fetchCart(user.id()))     // AsyncResult<Cart, OrderError>
///     .map(cart -> cart.toOrder())                // AsyncResult<Order, OrderError>
///     .peek(order -> metrics.recordOrder(order)); // side effect
/// ```
///
/// # Parallel Collection
/// ```java
/// List<AsyncResult<Product, ShopError>> fetches = productIds.stream()
///     .map(this::fetchProduct)
///     .toList();
///
/// AsyncResult<List<Product>, ShopError> allProducts = AsyncResult.collectAll(fetches);
/// ```
///
/// @param <T> The type of the success value.
/// @param <E> The type of the domain error.
public sealed interface AsyncResult<T, E> permits AsyncResult.Pending, AsyncResult.Completed {

	/// Internal record representing an async computation in progress.
    ///
    /// This is the primary implementation, wrapping a CompletableFuture that
    /// will eventually produce a Result.
    ///
    /// @param future The underlying future of Result.
	record Pending<T, E>(CompletableFuture<Result<T, E>> future) implements AsyncResult<T, E> {
	}

	/// Internal record representing an already-completed async computation.
    ///
    /// Used as an optimization to avoid wrapping already-resolved Results
    /// in unnecessary CompletableFutures.
    ///
    /// @param result The completed Result.
	record Completed<T, E>(Result<T, E> result) implements AsyncResult<T, E> {
	}

	/// Creates an AsyncResult from an existing CompletableFuture of Result.
    ///
    /// Use this when you already have a future that produces Results, such as
    /// from a custom async operation.
    ///
    /// @param future The future to wrap.
    /// @return A new Pending AsyncResult.
	@Contract(value = "_ -> new", pure = true)
	static <T, E> @NonNull AsyncResult<T, E> of(CompletableFuture<Result<T, E>> future) {
		if (future.isDone()) {
			// Optimization: if already complete, avoid the wrapper
			try {
				return new Completed<>(future.getNow(null));
			} catch (final Exception _) {
				// Fall through to Pending if we can't extract
			}
		}
		return new Pending<>(future);
	}

	/// Creates an AsyncResult from a CompletionStage of Result.
    ///
    /// Convenience overload for APIs returning CompletionStage.
    ///
    /// @param stage The completion stage to wrap.
    /// @return A new AsyncResult.
	@Contract("_ -> new")
	static <T, E> @NonNull AsyncResult<T, E> fromStage(@NonNull CompletionStage<Result<T, E>> stage) {
		return of(stage.toCompletableFuture());
	}

	/// Wraps an existing Result in a completed AsyncResult.
    ///
    /// Useful for synchronous fallbacks or cached values in async chains.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, Error> result = cache.get(id)
    ///     .map(AsyncResult::<User, Error>completed)
    ///     .orElseGet(() -> fetchFromApi(id));
    /// ```
    ///
    /// @param result The result to wrap.
    /// @return An already-completed AsyncResult.
	@Contract("_ -> new")
	static <T, E> @NonNull AsyncResult<T, E> completed(Result<T, E> result) {
		return new Completed<>(result);
	}

	/// Wraps a CompletableFuture, catching exceptions as Failure.
    ///
    /// This is the primary entry point for integrating external async APIs.
    /// Any exception thrown during the future's execution is mapped to a Failure
    /// using the provided error mapper.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, UserError> userAsync = AsyncResult.attempt(
    ///     webClient.get()
    ///         .uri("/users/{id}", id)
    ///         .retrieve()
    ///         .bodyToMono(User.class)
    ///         .toFuture(),
    ///     ex -> switch (ex) {
    ///         case WebClientResponseException.NotFound _ -> new UserError.NotFound(id);
    ///         case WebClientResponseException w -> new UserError.HttpError(w.getStatusCode());
    ///         default -> new UserError.Unexpected(ex);
    ///     }
    /// );
    /// ```
    ///
    /// @param future The future to wrap.
    /// @param errorMapper Maps exceptions to domain errors.
    /// @return An AsyncResult that catches all exceptions.
	@Contract("_, _ -> new")
	static <T, E> @NonNull AsyncResult<T, E> attempt(@NonNull CompletableFuture<T> future,
			Function<Throwable, E> errorMapper) {
		final CompletableFuture<Result<T, E>> resultFuture = future.thenApply(Result::<T, E>success)
				.exceptionally(ex -> Result.failure(errorMapper.apply(ex)));
		return new Pending<>(resultFuture);
	}

	/// Creates a completed successful AsyncResult.
    ///
    /// @param value The success value.
    /// @return A completed AsyncResult containing the value.
	@Contract("_ -> new")
	static <T, E> @NonNull AsyncResult<T, E> success(T value) {
		return completed(Result.success(value));
	}

	/// Creates a completed failed AsyncResult.
    ///
    /// @param error The domain error.
    /// @return A completed AsyncResult containing the error.
	@Contract("_ -> new")
	static <T, E> @NonNull AsyncResult<T, E> failure(E error) {
		return completed(Result.failure(error));
	}

	/// Collects multiple AsyncResults into one, short-circuiting on first failure.
    ///
    /// All input AsyncResults execute in parallel. Results are collected using
    /// the provided collector. If any AsyncResult fails, the entire collection
    /// fails with that error (fail-fast).
    ///
    /// Completed AsyncResults are optimized to not spawn additional futures.
    ///
    /// # Example
    /// ```java
    /// List<AsyncResult<Product, Error>> fetches = ids.stream()
    ///     .map(this::fetchProduct)
    ///     .toList();
    ///
    /// AsyncResult<List<Product>, Error> all = AsyncResult.collectAll(fetches);
    /// ```
    ///
    /// @param asyncResults The AsyncResults to collect.
    /// @param collector The collector to combine success values.
    /// @return An AsyncResult containing all values or first error.
	static <T, E, A, R> @NonNull AsyncResult<R, E> collectAll(@NonNull List<AsyncResult<T, E>> asyncResults,
			Collector<? super T, A, R> collector) {

		if (asyncResults.isEmpty()) {
			@SuppressWarnings("unchecked")
			final R emptyResult = (R) collector.supplier().get();
			return success(emptyResult);
		}

		// Optimization: check if all are already completed
		final boolean allCompleted = asyncResults.stream().allMatch(Completed.class::isInstance);
		if (allCompleted) {
			final List<Result<T, E>> results = asyncResults.stream().map(r -> ((Completed<T, E>) r).result()).toList();

			final Optional<E> firstError = results.stream().filter(Result::isFailure)
					.map(r -> ((Result.Failure<T, E>) r).error()).findFirst();

			if (firstError.isPresent()) {
				return failure(firstError.get());
			}

			final A accumulator = collector.supplier().get();
			final BiConsumer<A, ? super T> accumulatorAction = collector.accumulator();

			results.stream().map(r -> ((Result.Success<T, E>) r).value())
					.forEach(v -> accumulatorAction.accept(accumulator, v));

			return success(collector.finisher().apply(accumulator));
		}

		// General case: collect futures
		final List<CompletableFuture<Result<T, E>>> futures = asyncResults.stream().map(AsyncResult::toFuture).toList();

		final CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

		return new Pending<>(allDone.thenApply(_ -> {
			// Check for failures first (fail-fast)
			final Optional<E> firstError = futures.stream().map(CompletableFuture::join).filter(Result::isFailure)
					.map(r -> ((Result.Failure<T, E>) r).error()).findFirst();

			if (firstError.isPresent()) {
				return Result.failure(firstError.get());
			}

			// Collect all success values
			final A accumulator = collector.supplier().get();
			final BiConsumer<A, ? super T> accumulatorAction = collector.accumulator();

			futures.stream().map(CompletableFuture::join).map(r -> ((Result.Success<T, E>) r).value())
					.forEach(v -> accumulatorAction.accept(accumulator, v));

			final R finished = collector.finisher().apply(accumulator);
			return Result.success(finished);
		}));
	}

	/// Convenience overload collecting into a List.
    ///
    /// @param asyncResults The AsyncResults to collect.
    /// @return An AsyncResult containing a List of all values.
	static <T, E> @NonNull AsyncResult<List<T>, E> collectAll(List<AsyncResult<T, E>> asyncResults) {
		return collectAll(asyncResults, Collectors.toList());
	}

	/// Sequences a list of AsyncResults, preserving order.
    ///
    /// Alias for [this.collectAll] with List collector. Semantically clearer when
    /// you just want all results in order.
    ///
    /// @param asyncResults The AsyncResults to sequence.
    /// @return An AsyncResult containing all values in order.
	static <T, E> @NonNull AsyncResult<List<T>, E> sequence(List<AsyncResult<T, E>> asyncResults) {
		return collectAll(asyncResults);
	}

	/// Races multiple AsyncResults, returning the first to complete.
    ///
    /// The first AsyncResult to complete (successfully or with failure) wins.
    /// Other results are discarded.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, Error> fastest = AsyncResult.race(
    ///     fetchFromCache(id),
    ///     fetchFromDatabase(id),
    ///     fetchFromReplica(id)
    /// );
    /// ```
    ///
    /// @param first The first contender.
    /// @param others Additional contenders.
    /// @return An AsyncResult completing with the fastest result.
	@Contract("_, _ -> new")
	@SafeVarargs
	static <T, E> @NonNull AsyncResult<T, E> race(@NonNull AsyncResult<T, E> first,
			AsyncResult<T, E> @NonNull... others) {

		final List<CompletableFuture<Result<T, E>>> futures = new ArrayList<>(1 + others.length);
		futures.add(first.toFuture());
		futures.addAll(Arrays.stream(others).map(AsyncResult::toFuture).toList());

		return new Pending<>(CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).thenApply(r -> {
			@SuppressWarnings("unchecked")
			final Result<T, E> result = (Result<T, E>) r;
			return result;
		}));
	}

	/// Delays the execution of an AsyncResult.
    ///
    /// Useful for retries with backoff or rate limiting.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, Error> withBackoff = AsyncResult.delay(
    ///     fetchUser(id),
    ///     Duration.ofMillis(100 * attemptNumber)
    /// );
    /// ```
    ///
    /// @param asyncResult The AsyncResult to delay.
    /// @param delay The duration to delay.
    /// @return A delayed AsyncResult.
	@Contract("_, _ -> new")
	static <T, E> @NonNull AsyncResult<T, E> delay(@NonNull AsyncResult<T, E> asyncResult, Duration delay) {
		return switch (asyncResult) {
			case Completed<T, E>(var result) -> new Pending<>(CompletableFuture.supplyAsync(() -> result,
					CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)));
			case Pending<T, E>(var future) ->
				new Pending<>(future.thenCompose(result -> CompletableFuture.supplyAsync(() -> result,
						CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS))));
		};
	}

	/// Transforms the success value when this completes.
    ///
    /// If this AsyncResult completes with Failure, the mapper is not invoked
    /// and the Failure propagates unchanged.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<String, Error> nameAsync = userAsync.map(User::name);
    /// ```
    ///
    /// @param mapper Function to transform the success value.
    /// @param <U> The new success type.
    /// @return A new AsyncResult with the transformed type.
	@Contract("_ -> new")
	default <U> @NonNull AsyncResult<U, E> map(Function<T, U> mapper) {
		return switch (this) {
			case Completed<T, E>(var result) -> completed(result.map(mapper));
			case Pending<T, E>(var future) -> new Pending<>(future.thenApply(r -> r.map(mapper)));
		};
	}

	/// Transforms the error when this completes with Failure.
    ///
    /// If this AsyncResult completes with Success, the mapper is not invoked.
    /// Useful for translating error types between layers.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, ApiError> apiResult = dbResult.mapError(
    ///     dbErr -> new ApiError("Database error", dbErr.code())
    /// );
    /// ```
    ///
    /// @param mapper Function to transform the error.
    /// @param <F> The new error type.
    /// @return A new AsyncResult with the transformed error type.
	@Contract("_ -> new")
	default <F> @NonNull AsyncResult<T, F> mapError(Function<E, F> mapper) {
		return switch (this) {
			case Completed<T, E>(var result) -> completed(result.mapError(mapper));
			case Pending<T, E>(var future) -> new Pending<>(future.thenApply(r -> r.mapError(mapper)));
		};
	}

	/// Maps both success and error types simultaneously.
    ///
    /// This is a convenience method that applies transformations to both
    /// possible outcomes in a single operation.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<UserDTO, ApiError> apiResult = dbResult.mapBoth(
    ///     user -> new UserDTO(user.id(), user.name()),
    ///     dbErr -> new ApiError("DB_ERROR", dbErr.message())
    /// );
    /// ```
    ///
    /// @param successMapper Function to transform the success value.
    /// @param errorMapper Function to transform the error.
    /// @param <U> The new success type.
    /// @param <F> The new error type.
    /// @return A new AsyncResult with both types transformed.
	@Contract("_, _ -> new")
	default <U, F> @NonNull AsyncResult<U, F> mapBoth(Function<T, U> successMapper, Function<E, F> errorMapper) {
		return switch (this) {
			case Completed<T, E>(var result) -> completed(result.mapBoth(successMapper, errorMapper));
			case Pending<T, E>(var future) ->
				new Pending<>(future.thenApply(r -> r.mapBoth(successMapper, errorMapper)));
		};
	}

	/// Chains an async operation that itself returns an AsyncResult.
    ///
    /// This is the core sequencing operation. If this completes with Failure,
    /// the chained operation is not executed and the Failure propagates.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<Order, Error> orderAsync = userAsync
    ///     .flatMap(user -> fetchCart(user.id()))  // returns AsyncResult<Cart, Error>
    ///     .flatMap(cart -> createOrder(cart));    // returns AsyncResult<Order, Error>
    /// ```
    ///
    /// @param mapper Function returning the next AsyncResult.
    /// @param <U> The new success type.
    /// @return A flattened AsyncResult.
	@Contract("_ -> new")
	default <U> @NonNull AsyncResult<U, E> flatMap(Function<T, AsyncResult<U, E>> mapper) {
		return switch (this) {
			case Completed<T, E>(var result) -> switch (result) {
				case Result.Success<T, E>(var value) -> mapper.apply(value);
				case Result.Failure<T, E>(var error) -> failure(error);
			};
			case Pending<T, E>(var future) -> new Pending<>(future.thenCompose(result -> switch (result) {
				case Result.Success<T, E>(var value) -> mapper.apply(value).toFuture();
				case Result.Failure<T, E>(var error) -> CompletableFuture.completedFuture(Result.failure(error));
			}));
		};
	}

	/// Combines this AsyncResult with another using a combining function.
    ///
    /// Both AsyncResults execute in parallel. If either fails, the combination
    /// fails with the first error encountered (fail-fast).
    ///
    /// # Example
    /// ```java
    /// AsyncResult<OrderSummary, Error> summaryAsync = userAsync.combine(
    ///     cartAsync,
    ///     (user, cart) -> new OrderSummary(user, cart)
    /// );
    /// ```
    ///
    /// @param other The other AsyncResult to combine with.
    /// @param combiner Function merging both success values.
    /// @param <U> The other success type.
    /// @param <V> The combined success type.
    /// @return An AsyncResult containing the combined value or first error.
	@Contract("_, _ -> new")
	default <U, V> @NonNull AsyncResult<V, E> combine(@NonNull AsyncResult<U, E> other, BiFunction<T, U, V> combiner) {
		// Optimization: if both completed, combine immediately
		if (this instanceof Completed<T, E>(var r1) && other instanceof Completed<U, E>(var r2)) {
			final Result<V, E> combined = switch (r1) {
				case Result.Failure<T, E>(var err) -> Result.failure(err);
				case Result.Success<T, E>(var v1) -> switch (r2) {
					case Result.Failure<U, E>(var err) -> Result.failure(err);
					case Result.Success<U, E>(var v2) -> Result.success(combiner.apply(v1, v2));
				};
			};
			return completed(combined);
		}

		return new Pending<>(this.toFuture().thenCombine(other.toFuture(), (r1, r2) -> switch (r1) {
			case Result.Failure<T, E>(var err) -> Result.failure(err);
			case Result.Success<T, E>(var v1) -> switch (r2) {
				case Result.Failure<U, E>(var err) -> Result.failure(err);
				case Result.Success<U, E>(var v2) -> Result.success(combiner.apply(v1, v2));
			};
		}));
	}

	/// Recovers from failure with a fallback value.
    ///
    /// If this completes with Failure, the recovery function is applied to
    /// produce a success value. Success values pass through unchanged.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, Error> userOrGuest = userAsync.recover(
    ///     err -> User.guest()
    /// );
    /// ```
    ///
    /// @param recovery Function producing a fallback value from error.
    /// @return An AsyncResult that never fails (unless recovery throws).
	@Contract("_ -> new")
	default @NonNull AsyncResult<T, E> recover(Function<E, T> recovery) {
		return switch (this) {
			case Completed<T, E>(var result) -> completed(result.recover(recovery));
			case Pending<T, E>(var future) -> new Pending<>(future.thenApply(r -> r.recover(recovery)));
		};
	}

	/// Recovers from failure with another AsyncResult.
    ///
    /// Use when the recovery itself is async (e.g., fallback to cache).
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, Error> userAsync = primaryAsync.recoverWith(
    ///     err -> cacheAsync.findById(id)
    /// );
    /// ```
    ///
    /// @param recovery Function producing a fallback AsyncResult.
    /// @return The original or recovered AsyncResult.
	@Contract("_ -> new")
	default @NonNull AsyncResult<T, E> recoverWith(Function<E, AsyncResult<T, E>> recovery) {
		return switch (this) {
			case Completed<T, E>(var result) -> switch (result) {
				case Result.Success<T, E> _ -> this;
				case Result.Failure<T, E>(var err) -> recovery.apply(err);
			};
			case Pending<T, E>(var future) -> new Pending<>(future.thenCompose(result -> switch (result) {
				case Result.Success<T, E> _ -> CompletableFuture.completedFuture(result);
				case Result.Failure<T, E>(var err) -> recovery.apply(err).toFuture();
			}));
		};
	}

	/// Executes a side effect on success without modifying the result.
    ///
    /// Useful for logging, metrics, or caching without breaking the chain.
    ///
    /// # Example
    /// ```java
    /// userAsync
    ///     .peek(user -> log.info("Loaded user: {}", user.id()))
    ///     .peek(user -> metrics.increment("user.loaded"));
    /// ```
    ///
    /// @param action Consumer to execute on success.
    /// @return This AsyncResult (fluent API).
	@Contract("_ -> new")
	default @NonNull AsyncResult<T, E> peek(Consumer<T> action) {
		return switch (this) {
			case Completed<T, E>(var result) -> completed(result.peek(action));
			case Pending<T, E>(var future) -> new Pending<>(future.thenApply(r -> r.peek(action)));
		};
	}

	/// Executes a side effect on failure without modifying the result.
    ///
    /// Useful for error logging or alerting.
    ///
    /// # Example
    /// ```java
    /// userAsync.peekFailure(err -> alertService.notify("User load failed", err));
    /// ```
    ///
    /// @param action Consumer to execute on failure.
    /// @return This AsyncResult (fluent API).
	@Contract("_ -> new")
	default @NonNull AsyncResult<T, E> peekFailure(Consumer<E> action) {
		return switch (this) {
			case Completed<T, E>(var result) -> {
				if (result instanceof Result.Failure<T, E>(var err)) {
					action.accept(err);
				}
				yield this;
			}
			case Pending<T, E>(var future) -> new Pending<>(future.thenApply(result -> {
				if (result instanceof Result.Failure<T, E>(var err)) {
					action.accept(err);
				}
				return result;
			}));
		};
	}

	/// Filters the success value with a predicate.
    ///
    /// If the predicate returns false, the AsyncResult completes with the
    /// supplied error. Failures pass through unchanged.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, UserError> activeUser = userAsync.filter(
    ///     User::isActive,
    ///     () -> new UserError.Inactive(userId)
    /// );
    /// ```
    ///
    /// @param predicate Condition to test the success value.
    /// @param errorSupplier Provides error if predicate fails.
    /// @return An AsyncResult filtered by the predicate.
	@Contract("_, _ -> new")
	default @NonNull AsyncResult<T, E> filter(Predicate<T> predicate, Supplier<E> errorSupplier) {
		return switch (this) {
			case Completed<T, E>(var result) -> completed(switch (result) {
				case Result.Success<T, E>(var value) ->
					predicate.test(value) ? result : Result.failure(errorSupplier.get());
				case Result.Failure<T, E> _ -> result;
			});
			case Pending<T, E>(var future) -> new Pending<>(future.thenApply(result -> switch (result) {
				case Result.Success<T, E>(var value) ->
					predicate.test(value) ? result : Result.failure(errorSupplier.get());
				case Result.Failure<T, E> _ -> result;
			}));
		};
	}

	/// Returns a new AsyncResult that times out after the specified duration.
    ///
    /// If the original future doesn't complete in time, it completes with
    /// the supplied error.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, Error> timed = userAsync.timeout(
    ///     Duration.ofSeconds(5),
    ///     () -> new Error.Timeout()
    /// );
    /// ```
    ///
    /// @param duration Maximum time to wait.
    /// @param errorSupplier Provides timeout error.
    /// @return A timeout-guarded AsyncResult.
	@Contract("_, _ -> new")
	default @NonNull AsyncResult<T, E> timeout(@NonNull Duration duration, Supplier<E> errorSupplier) {
		return switch (this) {
			case Completed<T, E> _ -> this; // Already done, no need to timeout
			case Pending<T, E>(var future) -> {
				final CompletableFuture<Result<T, E>> timeoutFuture = new CompletableFuture<>();

				future.orTimeout(duration.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
						.whenComplete((result, ex) -> {
							if (ex != null) {
								timeoutFuture.complete(Result.failure(errorSupplier.get()));
							} else {
								timeoutFuture.complete(result);
							}
						});

				yield new Pending<>(timeoutFuture);
			}
		};
	}

	/// Converts to a plain CompletableFuture of Result.
    ///
    /// Use this when integrating with other async APIs or for final execution.
    ///
    /// @return The underlying future.
	@Contract(pure = true)
	default CompletableFuture<Result<T, E>> toFuture() {
		return switch (this) {
			case Completed<T, E>(var result) -> CompletableFuture.completedFuture(result);
			case Pending<T, E>(var future) -> future;
		};
	}

	/// Blocks and awaits the result.
    ///
    /// # ⚠️ Warning
    /// This blocks the calling thread. Use only at application boundaries
    /// (e.g., in controllers) or for testing. Never call this in async
    /// code without managing the blocking properly.
    ///
    /// @return The completed Result.
	default Result<T, E> join() {
		return this.toFuture().join();
	}

	/// Returns whether this AsyncResult has completed.
    ///
    /// @return true if completed (successfully or exceptionally).
	default boolean isDone() {
		return switch (this) {
			case Completed<T, E> _ -> true;
			case Pending<T, E>(var future) -> future.isDone();
		};
	}

	/// Returns whether this AsyncResult completed with Failure.
    ///
    /// Only meaningful after completion. Returns false if not done or if successful.
    ///
    /// @return true if completed with Failure.
	default boolean isCompletedExceptionally() {
		return switch (this) {
			case Completed<T, E>(var result) -> result.isFailure();
			case Pending<T, E>(var future) -> future.isCompletedExceptionally();
		};
	}

	/// Retries this AsyncResult with a fixed number of attempts.
    ///
    /// If the AsyncResult fails, it is retried up to the specified number
    /// of times with no delay between attempts.
    ///
    /// ⚠️ Warning: Immediate retries can overwhelm failing services.
    /// Consider using [this.retryWithBackoff] for production code.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, Error> resilient = userAsync.retry(3);
    /// ```
    ///
    /// @param maxAttempts Maximum number of attempts (must be >= 1).
    /// @return A retrying AsyncResult.
	default AsyncResult<T, E> retry(int maxAttempts) {
		if (maxAttempts <= 1) {
			return this;
		}
		return this.recoverWith(err -> this.retry(maxAttempts - 1));
	}

	/// Retries with exponential backoff.
    ///
    /// # Example
    /// ```java
    /// AsyncResult<User, Error> resilient = userAsync.retryWithBackoff(
    ///     3,                    // max attempts
    ///     Duration.ofMillis(100) // initial delay
    /// );
    /// ```
    ///
    /// @param maxAttempts Maximum number of attempts.
    /// @param initialDelay Initial delay, doubled each retry.
    /// @return A retrying AsyncResult with backoff.
	default AsyncResult<T, E> retryWithBackoff(int maxAttempts, java.time.Duration initialDelay) {
		return this.retryWithBackoff(maxAttempts, initialDelay, 2.0);
	}

	/// Retries with configurable exponential backoff.
    ///
    /// @param maxAttempts Maximum number of attempts.
    /// @param initialDelay Initial delay.
    /// @param multiplier Factor to multiply delay by each attempt.
    /// @return A retrying AsyncResult with backoff.
	default AsyncResult<T, E> retryWithBackoff(int maxAttempts, java.time.Duration initialDelay, double multiplier) {
		if (maxAttempts <= 1) {
			return this;
		}
		return this.recoverWith(_ -> {
			final AsyncResult<T, E> retried = delay(this, initialDelay);
			final java.time.Duration nextDelay = java.time.Duration
					.ofMillis((long) (initialDelay.toMillis() * multiplier));
			return retried.retryWithBackoff(maxAttempts - 1, nextDelay, multiplier);
		});
	}
}
