package dev.jose.result.utils;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// Test utilities for safely creating null values.
///
/// Use these to suppress @NonNull warnings when testing null handling.
public final class TestNulls {
	private TestNulls() {
	}

	/// Returns null as a non-null type. Use only in tests.
  ///
  /// @param <T> the declared type (will be null at runtime)
  /// @return always null
	@SuppressWarnings("all") // Suppresses nullness warnings
	public static <T> @NonNull T n() {
		return (@Nullable T) null;
	}

	/// Casts any value to non-null. Use only in tests.
  ///
  /// @param value the value (may be null)
  /// @param <T>   the type
  /// @return the same value, with non-null type
	@SuppressWarnings("all")
	public static <T> @NonNull T unchecked(@Nullable T value) {
		return (@NonNull T) value;
	}
}
