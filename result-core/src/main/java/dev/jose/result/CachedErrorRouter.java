package dev.jose.result;

import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/// A **decorator** that adds caching to an [ErrorRouter].
///
/// Exception type lookup is performed once per unique exception class, with
/// the result cached for subsequent lookups. This is beneficial when:
///
/// - The router has many rules (linear scan becomes expensive)
/// - The same exception types are encountered repeatedly
/// - Routing is on a hot path
///
/// ## Thread Safety
///
/// This class is **thread-safe**. It uses a [ConcurrentHashMap] for the
/// cache, allowing concurrent reads and safe concurrent writes.
///
/// ## Memory Considerations
///
/// The cache grows unbounded — it stores one entry per unique exception
/// class encountered. For long-running applications with many exception
/// types, consider using a bounded cache like Caffeine instead.
///
/// ## Usage
///
/// ```java
/// var coreRouter = ErrorRouter
///     .<AppError>defaultsTo(ex -> AppError.UNKNOWN)
///     .map(IOException.class, ex -> AppError.IO_ERROR)
///     .map(SQLException.class, ex -> AppError.DATABASE_ERROR);
///
/// // Wrap with caching for hot paths
/// var cachedRouter = new CachedErrorRouter<>(coreRouter);
/// ```
///
/// ## Composition
///
/// Combine with [MeteredErrorRouter] for full observability:
///
/// ```java
/// var router = new CachedErrorRouter<>(
///     new MeteredErrorRouter<>(
///         ErrorRouter.defaultsTo(ex -> AppError.UNKNOWN),
///         meterRegistry,
///         "errors"
///     )
/// );
/// ```
///
/// @param <E> the domain error type
/// @see ErrorRouter for the core routing logic
/// @see MeteredErrorRouter for metrics
/// @author Jose
/// @since 1.0.0
public final class CachedErrorRouter<E> implements Function<Exception, E> {

	private final ErrorRouter<E> delegate;
	private final ConcurrentHashMap<Class<?>, Optional<E>> cache;

	/// Creates a new cached error router wrapping the given delegate.
  ///
  /// @param delegate the underlying error router to cache results from
  /// @throws NullPointerException if `delegate` is null
	public CachedErrorRouter(@NonNull ErrorRouter<E> delegate) {
		this.delegate = Objects.requireNonNull(delegate, "Delegate ErrorRouter cannot be null");
		this.cache = new ConcurrentHashMap<>();
	}

	/// Applies the delegate router, caching the result by exception class.
  ///
  /// Subsequent calls with exceptions of the same class will return the
  /// cached result without re-evaluating routing rules. The delegate evaluation
  /// is **atomic** — even under concurrent load, the delegate is guaranteed
  /// to be invoked at most once per unique exception class.
  ///
  /// ## Null Handling
  ///
  /// If the delegate router maps an exception to `null`, this `null` result
  /// is safely cached. Subsequent lookups for that exception class will
  /// correctly return `null` without invoking the delegate again.
  ///
  /// ## Note on Subclasses
  ///
  /// The cache key is the **runtime class** of the exception. A
  /// `FileNotFoundException` and a `SocketException` will have separate
  /// cache entries even if both map to the same error via `IOException`.
  ///
  /// @param exception the exception to map
  /// @return the domain error (possibly from cache, may be null)
  /// @throws NullPointerException if `exception` is null
	@Override
	public E apply(@NonNull Exception exception) {
		final Class<?> key = exception.getClass();

		// Use computeIfAbsent to guarantee atomic execution and thread-safety
		final Optional<E> resultOpt = this.cache.computeIfAbsent(key,
				_ -> Optional.ofNullable(this.delegate.apply(exception)));

		return resultOpt.orElse(null);
	}

	/// Returns the current cache size (number of unique exception classes).
  ///
  /// @return the number of cached mappings
	public int cacheSize() {
		return this.cache.size();
	}

	/// Clears all cached mappings.
  ///
  /// Subsequent calls will re-evaluate routing rules.
	public void clearCache() {
		this.cache.clear();
	}

	/// Returns the underlying delegate router.
  ///
  /// @return the wrapped `ErrorRouter`
	public @NonNull ErrorRouter<E> delegate() {
		return this.delegate;
	}
}
