package dev.jose.result;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/// A simple validator utility for validating objects and collecting errors.
/// @param <T> The type of the object to validate.
public record Validator<T>(T target, Map<String, String> errors) {

	/// Creates a new Validator for the given target object.
    ///
    /// @param target The object to validate.
    /// @return A new Validator instance with no errors.
	@Contract("_ -> new")
	public static <T> @NonNull Validator<T> of(T target) {
		return new Validator<>(target, Map.of());
	}

	/// Composes multiple validation functions into a single validator.
    ///
    /// # Example
    /// ```java
  /// Function<Validator<User>, Validator<User>> emailValidation =
  ///     v -> v.required(User::email, "email")
  ///          .validate(u -> u.email().contains("@"), "email", "Invalid format");
  ///
  /// Validator.compose(user, emailValidation, ageValidation);
  /// ```
    ///
    /// @param target      The object to validate.
    /// @param validations Variable number of validation functions.
    /// @return The final Validator after applying all validations.
	@SafeVarargs
	public static <T> Validator<T> compose(T target, UnaryOperator<Validator<T>> @NonNull... validations) {
		Validator<T> validator = of(target);
		for (final var validation : validations) {
			validator = validation.apply(validator);
		}
		return validator;
	}

	/// Validates the target object against the given condition.
    ///
    /// If the condition fails, an error message is added for the specified field.
    ///
    /// @param condition The predicate to test the target object.
    /// @param field     The field name associated with the error.
    /// @param message   The error message to add if the condition fails.
    /// @return A new Validator instance with the potential error added.
	public Validator<T> validate(@NonNull Predicate<T> condition, String field, String message) {
		if (!condition.test(this.target)) {
			return this.withError(field, message);
		}
		return this;
	}

	/// Conditionally applies validation logic.
    ///
    /// Only runs the validation function if the condition is met.
    ///
    /// # Example
    /// ```java
  /// validator.validateIf(
  ///     user -> user.isPremium(),
  ///     v -> v.nonNull(User::billingAddress, "billingAddress")
  /// );
  /// ```
    ///
    /// @param condition The condition to test.
    /// @param validation The validation to apply if condition is true.
    /// @return The validator after potentially applying the validation.
	public Validator<T> validateIf(@NonNull Predicate<T> condition, UnaryOperator<Validator<T>> validation) {
		return condition.test(this.target) ? validation.apply(this) : this;
	}

	/// Validates a cross-field constraint.
    ///
    /// If the condition fails, the same error message is added to all specified fields.
    ///
    /// # Example
    /// ```java
  /// validator.validateFields(
  ///     user -> user.password().equals(user.confirmPassword()),
  ///     "Passwords must match",
  ///     "password", "confirmPassword"
  /// );
  /// ```
    ///
    /// @param condition The predicate to test.
    /// @param message   The error message.
    /// @param fields    The field names to associate with the error.
    /// @return A new Validator with potential errors added.
	public Validator<T> validateFields(@NonNull Predicate<T> condition, String message, String... fields) {
		if (!condition.test(this.target)) {
			final Map<String, String> newErrors = new HashMap<>(this.errors);
			for (final String field : fields) {
				newErrors.put(field, message);
			}
			return new Validator<>(this.target, newErrors);
		}
		return this;
	}

	/// Validates an Optional field if present.
    ///
    /// If the Optional is empty, no validation is performed.
    ///
    /// # Example
    /// ```java
  /// validator.validateOptional(
  ///     User::middleName,
  ///     name -> name.length() > 1,
  ///     "middleName",
  ///     "Middle name must be at least 2 characters"
  /// );
  /// ```
    ///
    /// @param extractor Function to extract the Optional field.
    /// @param condition Predicate to test the unwrapped value.
    /// @param field     The field name.
    /// @param message   The error message.
    /// @return A new Validator with potential error added.
	public <U> Validator<T> validateOptional(@NonNull Function<T, Optional<U>> extractor, Predicate<U> condition,
			String field, String message) {
		final Optional<U> value = extractor.apply(this.target);
		if (value.isPresent() && !condition.test(value.get())) {
			return this.withError(field, message);
		}
		return this;
	}

	/// Validates that the extracted string field is not null or blank.
    ///
    /// If the field is null or blank, an error message is added for the specified field.
    ///
    /// @param extractor A function to extract the string field from the target object.
    /// @param field     The field name associated with the error.
    /// @return A new Validator instance with the potential error added.
	public Validator<T> required(@NonNull Function<T, String> extractor, String field) {
		final String value = extractor.apply(this.target);
		if (value == null || value.isBlank()) {
			return this.withError(field, "Field is required");
		}
		return this;
	}

	/// Validates that the extracted string field matches the given pattern.
    ///
    /// @param extractor A function to extract the string field.
    /// @param pattern   The regex pattern to match.
    /// @param field     The field name.
    /// @param message   The error message if pattern doesn't match.
    /// @return A new Validator instance with the potential error added.
	public Validator<T> matches(@NonNull Function<T, String> extractor, String pattern, String field, String message) {
		final String value = extractor.apply(this.target);
		if (value != null && !value.matches(pattern)) {
			return this.withError(field, message);
		}
		return this;
	}

	/// Validates that the extracted field is not null.
    ///
    /// If the field is null, an error message is added for the specified field.
    ///
    /// @param extractor A function to extract the field from the target object.
    /// @param field     The field name associated with the error.
    /// @return A new Validator instance with the potential error added.
	public Validator<T> nonNull(@NonNull Function<T, Object> extractor, String field) {
		final Object value = extractor.apply(this.target);
		if (value == null) {
			return this.withError(field, "Field must not be null");
		}
		return this;
	}

	/// Validates that the extracted numeric field is positive.
    ///
    /// If the field is null or not positive, an error message is added for the specified field.
    ///
    /// @param extractor A function to extract the numeric field from the target object.
    /// @param field     The field name associated with the error.
    /// @return A new Validator instance with the potential error added.
	public Validator<T> positive(@NonNull Function<T, Number> extractor, String field) {
		final Number value = extractor.apply(this.target);
		if (value == null || value.doubleValue() <= 0) {
			return this.withError(field, "Must be positive");
		}
		return this;
	}

	/// Validates that the extracted numeric field is within a range.
    ///
    /// @param extractor A function to extract the numeric field.
    /// @param min       Minimum value (inclusive).
    /// @param max       Maximum value (inclusive).
    /// @param field     The field name.
    /// @return A new Validator instance with the potential error added.
	public Validator<T> range(@NonNull Function<T, Number> extractor, double min, double max, String field) {
		final Number value = extractor.apply(this.target);
		if (value != null) {
			final double val = value.doubleValue();
			if (val < min || val > max) {
				return this.withError(field, "Must be between " + min + " and " + max);
			}
		}
		return this;
	}

	/// Validates that the extracted string field's length is within bounds.
    ///
    /// @param extractor A function to extract the string field.
    /// @param min       Minimum length (inclusive).
    /// @param max       Maximum length (inclusive).
    /// @param field     The field name.
    /// @return A new Validator instance with the potential error added.
	public Validator<T> length(@NonNull Function<T, String> extractor, int min, int max, String field) {
		final String value = extractor.apply(this.target);
		if (value != null) {
			final int len = value.length();
			if (len < min || len > max) {
				return this.withError(field, "Length must be between " + min + " and " + max);
			}
		}
		return this;
	}

	/// Returns the validation result as a standard Result.
    ///
    /// If there are no errors, returns a successful Result containing the target object.
    /// If there are errors, returns a failure Result containing the error map.
    ///
    /// @return A Result representing the outcome of the validation.
	public Result<T, Map<String, String>> result() {
		return this.errors.isEmpty() ? Result.success(this.target) : Result.failure(this.errors);
	}

	/// Returns the validation result with a custom error type.
    ///
    /// This is the recommended method as it allows type-safe error handling.
    ///
    /// # Example
    /// ```java
  /// Result<User, ValidationError> result = validator
  ///     .resultOr(ValidationError.FieldErrors::new);
  /// ```
    ///
    /// @param errorMapper Function to convert the error map to your error type.
    /// @return A Result with your custom error type.
	public <E> Result<T, E> resultOr(Function<Map<String, String>, E> errorMapper) {
		return this.errors.isEmpty() ? Result.success(this.target) : Result.failure(errorMapper.apply(this.errors));
	}

	/// Checks if the validator has any errors.
    ///
    /// @return true if there are validation errors, false otherwise.
	@Contract(pure = true)
	public boolean hasErrors() {
		return !this.errors.isEmpty();
	}

	/// Gets the number of validation errors.
    ///
    /// @return The count of errors.
	@Contract(pure = true)
	public int errorCount() {
		return this.errors.size();
	}

	/// Internal helper to create a new Validator with an additional error.
    ///
    /// @param field   The field name.
    /// @param message The error message.
    /// @return A new Validator instance.
	@Contract("_, _ -> new")
	private @NonNull Validator<T> withError(String field, String message) {
		final Map<String, String> newErrors = new HashMap<>(this.errors);
		newErrors.put(field, message);
		return new Validator<>(this.target, newErrors);
	}
}
