package dev.jose.result.utils;

import java.util.Map;

/// Framework-agnostic metadata for a domain failure.
///
/// A sealed domain-error hierarchy commonly extends this interface so that each
/// failure carries a human-readable message and stable presentation metadata.
/// Infrastructure adapters may consume this metadata without leaking their own
/// framework types into the domain model.
///
/// # Example
/// ```java
/// public sealed interface UserFailure extends Failure {
///   record NotFound(long userId) implements UserFailure {
///     @Override
///     public String getMessage() {
///       return "User " + this.userId + " was not found";
///     }
///
///     @Override
///     public Map<String, Object> getExtensions() {
///       return Map.of("userId", this.userId);
///     }
///   }
/// }
/// ```
public interface Failure {

  /// Returns the default human-readable message.
  ///
  /// Presentation adapters can use this value as the fallback when no localized
  /// message is available.
  ///
  /// @return the default message
  String getMessage();

  /// Returns arguments used to interpolate a localized message.
  ///
  /// @return message arguments, or an empty array when none are needed
  default Object[] getMessageArgs() {
    return new Object[0];
  }

  /// Returns the default presentation title.
  ///
  /// @return the simple failure class name by default
  default String getTitle() {
    return this.getClass().getSimpleName();
  }

  /// Returns a stable, machine-readable error code.
  ///
  /// @return the failure class name converted to upper snake case
  default String getErrorCode() {
    return this.getClass().getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  /// Returns additional presentation metadata.
  ///
  /// Presentation adapters can expose these entries as additional response
  /// properties.
  ///
  /// @return additional metadata, or an empty map by default
  default Map<String, Object> getExtensions() {
    return Map.of();
  }
}
