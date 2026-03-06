package dev.jose.result.utils;

import java.util.Locale;
import java.util.Map;

/// Base interface for all domain errors in the application.
///
/// Implementing this interface allows errors to be used with the Result pattern
/// and automatically handled by the Spring response advice.
///
/// # Features
/// - Message generation (with i18n support)
/// - Custom title and extensions for Problem Details
/// - Default implementations for common scenarios
///
/// # Usage
/// ```java
/// public sealed interface UserFailure extends BaseFailure {
///   @ResponseStatus(HttpStatus.NOT_FOUND)
///   record NotFound(String field, Object id) implements UserFailure {
///   }
/// }
/// ```
public interface BaseFailure {

	/// Returns the error message.
    ///
    /// This is the primary error description that will be shown to users
    /// or logged for debugging purposes.
    ///
    /// @return The error message.
	String getMessage();

	/// Returns the localized error message.
    ///
    /// Override this method to provide internationalization support.
    /// The default implementation falls back to getMessage().
    ///
    /// # Example Implementation
    /// ```java
    /// default String getMessage(Locale locale) {
    ///   return messageSource.getMessage(
    ///     "error." + getClass().getSimpleName(),
    ///     getMessageArgs(),
    ///     getMessage(),
    ///     locale
    ///   );
    /// }
    /// ```
    ///
    /// @param locale The target locale.
    /// @return The localized error message.
	default String getMessage(Locale locale) {
		return this.getMessage();
	}

	/// Returns the arguments to be used for message formatting.
    ///
    /// Used in conjunction with getMessage(Locale) for parameterized messages.
    ///
    /// # Example
    /// ```java
    /// // For message: "User {0} not found"
    /// default Object[] getMessageArgs() {
    ///   return new Object[] { userId };
    /// }
    /// ```
    ///
    /// @return Array of message arguments.
	default Object[] getMessageArgs() {
		return new Object[0];
	}

	/// Returns the error title for Problem Details.
    ///
    /// By default, uses the simple class name (e.g., "NotFound", "InvalidInput").
    /// Override to provide custom titles.
    ///
    /// @return The error title.
	default String getTitle() {
		return this.getClass().getSimpleName();
	}

	/// Returns additional properties to include in Problem Details.
    ///
    /// Use this to add custom metadata to your error responses.
    ///
    /// # Example
    /// ```java
    /// record NotFound(String resource, String id) implements BaseFailure {
    ///   @Override
    ///   public Map<String, Object> getExtensions() {
    ///     return Map.of(
    ///       "resource", resource,
    ///       "searchedId", id,
    ///       "timestamp", Instant.now()
    ///     );
    ///   }
    /// }
    /// ```
    ///
    /// @return Map of additional properties.
	default Map<String, Object> getExtensions() {
		return Map.of();
	}

	/// Returns the error code for this failure.
    ///
    /// Error codes provide a machine-readable identifier for the error type.
    /// Useful for client-side error handling and documentation.
    ///
    /// By default, generates a code from the class name (e.g., "USER_NOT_FOUND").
    ///
    /// @return The error code.
	default String getErrorCode() {
		return this.getClass().getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
	}

	/// Determines if this error should be logged.
    ///
    /// By default, all errors are logged. Override to suppress logging
    /// for expected errors (like validation failures).
    ///
    /// @return true if this error should be logged, false otherwise.
	default boolean shouldLog() {
		return true;
	}

	/// Returns the log level for this error.
    ///
    /// By default, returns WARN. Override to use ERROR for critical failures
    /// or INFO for expected business errors.
    ///
    /// @return The log level as a string.
	default String getLogLevel() {
		return "WARN";
	}
}
