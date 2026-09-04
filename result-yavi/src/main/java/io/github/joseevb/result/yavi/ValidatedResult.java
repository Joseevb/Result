package io.github.joseevb.result.yavi;

import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.core.Validatable;
import am.ik.yavi.core.Validated;
import io.github.joseevb.result.Result;
import java.util.Objects;

/// Adapts YAVI validation outcomes to [Result] without changing YAVI's error model.
public final class ValidatedResult {
  private ValidatedResult() {}

  /// Converts a YAVI [Validated] value to a [Result].
  ///
  /// A valid value becomes an [Result.Ok], while an invalid value becomes an [Result.Err]
  /// containing its YAVI [ConstraintViolations]. A valid YAVI value may contain `null`, but a
  /// `Result` cannot; consequently, converting `Validated.successWith(null)` throws a
  /// [NullPointerException].
  ///
  /// @param validated the YAVI value to convert
  /// @param <T> the successful value type
  /// @return the equivalent Result
  public static <T> Result<T, ConstraintViolations> from(Validated<? extends T> validated) {
    Objects.requireNonNull(validated, "validated cannot be null");
    return validated.isValid() ? Result.ok(validated.value()) : Result.err(validated.errors());
  }

  /// Validates an existing value with YAVI and returns the original value on success.
  ///
  /// @param target the value to validate
  /// @param validator the YAVI validator to use
  /// @param <T> the value type
  /// @return an Ok containing `target`, or an Err containing the violations returned by YAVI
  public static <T> Result<T, ConstraintViolations> validate(
      T target, Validatable<? super T> validator) {
    Objects.requireNonNull(validator, "validator cannot be null");
    final ConstraintViolations violations = validator.validate(target);
    return violations.isValid() ? Result.ok(target) : Result.err(violations);
  }
}
