package dev.jose.result;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// A declarative utility for mapping Java Exceptions to Domain Errors.
///
/// This class eliminates verbose `try-catch` blocks and `switch` statements in Service layers.
/// It allows you to define exception mapping logic once and reuse it across multiple methods.
///
/// # Features
/// - Declarative exception-to-error mapping
/// - Built-in logging support
/// - Metrics/observability integration via Micrometer
/// - Order-aware rule evaluation
/// - Shadowing detection for debugging
///
/// # Usage Example
/// ```java
/// // Define the router once (e.g., in your Error Interface)
/// Function<Exception, UserError> ERROR_MAPPER = ErrorRouter
///     .defaultsTo(e -> new UserError.SystemFailure(e.getMessage()))
///     .withLogging(log) // Optional: add logging
///     .withMetrics(meterRegistry, "user.errors") // Optional: add metrics
///     .map(IllegalArgumentException.class, e -> new UserError.InvalidInput(e.getMessage()))
///     .map(DataIntegrityViolationException.class, _ -> new UserError.EmailExists("Taken"));
///
/// // Use it in your service
/// return Result.attempt(
///     () -> repository.save(entity),
///     ERROR_MAPPER
/// );
/// ```
///
/// @param <E> The type of Domain Error (usually a Sealed Interface)
public class ErrorRouter<E> implements Function<Exception, E> {

	private final Map<Class<? extends Exception>, Function<Exception, E>> registry = new LinkedHashMap<>();

	private final Function<Exception, E> fallback;

	private final Logger logger;

	private final MeterRegistry meterRegistry;

	private final String metricPrefix;

	private final boolean enableShadowWarnings;

	private ErrorRouter(Function<Exception, E> fallback, Logger logger, MeterRegistry meterRegistry,
			String metricPrefix, boolean enableShadowWarnings) {
		this.fallback = fallback;
		this.logger = logger;
		this.meterRegistry = meterRegistry;
		this.metricPrefix = metricPrefix;
		this.enableShadowWarnings = enableShadowWarnings;
	}

	/// Creates a new `ErrorRouter` with a mandatory fallback strategy.
    ///
    /// This is the entry point for the builder. The fallback function will be executed
    /// if an exception is thrown that does not match any specifically mapped classes.
    ///
    /// @param fallback A function that converts any unhandled `Exception` into error type `E`.
    /// @return A new instance of `ErrorRouter`.
	@Contract("_ -> new")
	public static <E> @NonNull ErrorRouter<E> defaultsTo(Function<Exception, E> fallback) {
		return new ErrorRouter<>(fallback, null, null, null, false);
	}

	/// Enables logging for all exception mappings.
    ///
    /// When enabled, each exception mapping will be logged at WARN level.
    ///
    /// @param logger The SLF4J logger to use.
    /// @return This instance for method chaining.
	public ErrorRouter<E> withLogging(Logger logger) {
		return new ErrorRouter<>(this.fallback, logger, this.meterRegistry, this.metricPrefix,
				this.enableShadowWarnings);
	}

	/// Enables metrics/observability integration via Micrometer.
    ///
    /// Creates counters for each error type mapped.
    ///
    /// # Example
    /// ```java
  /// router.withMetrics(meterRegistry, "app.errors")
  ///     // Creates counters like: app.errors.IllegalArgumentException
  /// ```
    ///
    /// @param meterRegistry The Micrometer MeterRegistry.
    /// @param metricPrefix  The prefix for metric names.
    /// @return This instance for method chaining.
	public ErrorRouter<E> withMetrics(MeterRegistry meterRegistry, String metricPrefix) {
		return new ErrorRouter<>(this.fallback, this.logger, meterRegistry, metricPrefix, this.enableShadowWarnings);
	}

	/// Enables shadow detection warnings.
    ///
    /// When enabled, warns if a more specific exception is registered after
    /// a more general one (which would cause the specific one to be shadowed).
    ///
    /// Useful during development to catch configuration mistakes.
    ///
    /// @return This instance for method chaining.
	public ErrorRouter<E> withShadowWarnings() {
		return new ErrorRouter<>(this.fallback, this.logger, this.meterRegistry, this.metricPrefix, true);
	}

	/// Registers a specific mapping rule for a given Exception type.
    ///
    /// # Order Matters
    /// Rules are evaluated in the order they are defined. If you map `RuntimeException`
    /// before `IllegalArgumentException`, the first one will win. Always map specific
    /// exceptions before generic ones.
    ///
    /// # Shadowing Detection
    /// If shadow warnings are enabled, this method will warn you if you're registering
    /// a rule that will never be matched due to a previously registered parent class.
    ///
    /// @param type   The class of the exception to handle (e.g., `IllegalArgumentException.class`).
    /// @param mapper A function that converts this specific exception `X` into error type `E`.
    /// @return This instance for method chaining.
	public <X extends Exception> ErrorRouter<E> map(Class<X> type, Function<X, E> mapper) {
		// Shadow detection
		if (this.enableShadowWarnings && this.logger != null) {
			this.registry.keySet().stream()
					.filter(existing -> existing.isAssignableFrom(type) && !existing.equals(type)).findFirst()
					.ifPresent(shadowing -> this.logger.warn(
							"ErrorRouter: {} will be shadowed by {} - reorder your mappings", type.getSimpleName(),
							shadowing.getSimpleName()));
		}

		this.registry.put(type, ex -> mapper.apply(type.cast(ex)));
		return this;
	}

	/// Registers a mapping for an exception and all its subclasses.
    ///
    /// This is a convenience method that's semantically clearer when you intend
    /// to handle an entire exception hierarchy.
    ///
    /// @param baseType The base exception class.
    /// @param mapper   Function to map the exception.
    /// @return This instance for method chaining.
	public <X extends Exception> ErrorRouter<E> mapAll(Class<X> baseType, Function<X, E> mapper) {
		return this.map(baseType, mapper);
	}

	/// Registers a mapping with custom side effects.
    ///
    /// Allows you to perform additional actions when a specific exception is caught,
    /// such as custom logging or alerting.
    ///
    /// # Example
    /// ```java
  /// router.mapWithEffect(
  ///     SQLException.class,
  ///     (ex, error) -> alerting.sendAlert("Database error", ex),
  ///     ex -> new DbError(ex.getMessage())
  /// );
  /// ```
    ///
    /// @param type      The exception class to handle.
    /// @param sideEffect Action to perform when this exception is caught.
    /// @param mapper    Function to map the exception to domain error.
    /// @return This instance for method chaining.
	public <X extends Exception> ErrorRouter<E> mapWithEffect(Class<X> type, BiConsumer<X, E> sideEffect,
			Function<X, E> mapper) {
		return this.map(type, ex -> {
			final E error = mapper.apply(ex);
			sideEffect.accept(ex, error);
			return error;
		});
	}

	/// Executes the mapping logic.
    ///
    /// Iterates through registered rules. The first rule where `ruleKey.isInstance(e)`
    /// returns true is executed. If no match is found, the fallback is used.
    ///
    /// Side effects (logging, metrics) are performed automatically if configured.
    ///
    /// @param e The exception that occurred.
    /// @return The mapped Domain Error.
	@Override
	public E apply(Exception e) {
		// Find matching rule
		final var matchedEntry = this.registry.entrySet().stream().filter(entry -> entry.getKey().isInstance(e))
				.findFirst();

		final E error;
		final String exceptionType;

		if (matchedEntry.isPresent()) {
			exceptionType = matchedEntry.get().getKey().getSimpleName();
			error = matchedEntry.get().getValue().apply(e);
		} else {
			exceptionType = e.getClass().getSimpleName() + " (unmapped)";
			error = this.fallback.apply(e);
		}

		// Perform side effects
		this.recordLog(e, error, exceptionType);
		this.recordMetric(exceptionType);

		return error;
	}

	/// Records a log entry if logging is enabled.
    ///
    /// @param exception     The original exception.
    /// @param mappedError   The resulting domain error.
    /// @param exceptionType The exception type name.
	private void recordLog(Exception exception, E mappedError, String exceptionType) {
		if (this.logger != null) {
			this.logger.warn("Exception mapped: {} -> {}", exceptionType, mappedError.getClass().getSimpleName(),
					exception);
		}
	}

	/// Records a metric if metrics are enabled.
    ///
    /// @param exceptionType The exception type name.
	private void recordMetric(String exceptionType) {
		if (this.meterRegistry != null && this.metricPrefix != null) {
			Counter.builder(this.metricPrefix).tag("exception", exceptionType)
					.description("Count of exceptions mapped to domain errors").register(this.meterRegistry)
					.increment();
		}
	}

	/// Returns the number of registered exception mappings.
    ///
    /// Useful for testing and debugging.
    ///
    /// @return The count of registered mappings.
	public int mappingCount() {
		return this.registry.size();
	}

	/// Checks if a specific exception type has a registered mapping.
    ///
    /// @param type The exception class to check.
    /// @return true if a mapping exists for this exact type.
	public boolean hasMappingFor(Class<? extends Exception> type) {
		return this.registry.containsKey(type);
	}
}
