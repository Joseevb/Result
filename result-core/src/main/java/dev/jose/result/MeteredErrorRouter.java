package dev.jose.result;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.function.Function;

/// A **decorator** that adds Micrometer metrics to an [ErrorRouter].
///
/// This class wraps an existing `ErrorRouter` and records metrics about exception
/// mapping operations without modifying the core routing behavior. Use this when
/// you need observability into error patterns in production.
///
/// ## Metrics Recorded
///
/// - **Counter**: `error.router.mappings` — incremented for each mapping operation
///   - Tag `exception`: the simple class name of the input exception
///   - Tag `mapped_to`: the simple class name of the output error
///
/// ## Usage
///
/// ```java
/// var coreRouter = ErrorRouter
///     .<AppError>defaultsTo(ex -> AppError.UNKNOWN)
///     .map(IOException.class, ex -> AppError.IO_ERROR);
///
/// var meteredRouter = new MeteredErrorRouter<>(
///     coreRouter,
///     meterRegistry,
///     "error.router.mappings"
/// );
/// ```
///
/// ## Composition
///
/// This decorator is designed to be **composable** with other wrappers:
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
/// @see CachedErrorRouter for caching
/// @author Jose
/// @since 1.0.0
public final class MeteredErrorRouter<E> implements Function<Exception, E> {

	private final ErrorRouter<E> delegate;
	private final MeterRegistry meterRegistry;
	private final String metricName;
	private final Iterable<Tag> commonTags;

	/// Creates a new metered error router.
  ///
  /// @param delegate      the underlying error router to wrap
  /// @param meterRegistry the Micrometer registry for recording metrics
  /// @param metricName    the base name for the counter metric
  /// @throws NullPointerException if any parameter is null
	public MeteredErrorRouter(@NonNull ErrorRouter<E> delegate, @NonNull MeterRegistry meterRegistry,
			@NonNull String metricName) {
		this(delegate, meterRegistry, metricName, Tags.empty());
	}

	/// Creates a new metered error router with additional common tags.
  ///
  /// @param delegate      the underlying error router to wrap
  /// @param meterRegistry the Micrometer registry for recording metrics
  /// @param metricName    the base name for the counter metric
  /// @param commonTags    tags to add to every metric recording
  /// @throws NullPointerException if any parameter is null
	public MeteredErrorRouter(@NonNull ErrorRouter<E> delegate, @NonNull MeterRegistry meterRegistry,
			@NonNull String metricName, @NonNull Iterable<Tag> commonTags) {
		this.delegate = Objects.requireNonNull(delegate, "Delegate ErrorRouter cannot be null");
		this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry cannot be null");
		this.metricName = Objects.requireNonNull(metricName, "Metric name cannot be null");
		this.commonTags = Objects.requireNonNull(commonTags, "Common Tags cannot be null");
	}

	/// Applies the delegate router and records metrics about the operation.
  ///
  /// Records:
  /// - The exception type that was mapped
  /// - The error type it was mapped to
  ///
  /// @param exception the exception to map
  /// @return the domain error from the delegate router
  /// @throws NullPointerException if `exception` is null
	@Override
	public E apply(@NonNull Exception exception) {
		final E result = this.delegate.apply(exception);

		Counter.builder(this.metricName).tags(this.commonTags).tag("exception", exception.getClass().getSimpleName())
				.tag("mapped_to", result != null ? result.getClass().getSimpleName() : "null")
				.description("Count of exceptions mapped to domain errors").register(this.meterRegistry).increment();

		return result;
	}

	/// Returns the underlying delegate router.
  ///
  /// Useful for accessing `ruleCount()` or other methods not exposed
  /// by the `Function` interface.
  ///
  /// @return the wrapped `ErrorRouter`
	public @NonNull ErrorRouter<E> delegate() {
		return this.delegate;
	}
}
