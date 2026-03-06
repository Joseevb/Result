package dev.jose.result.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.Map;
import java.util.Optional;

/// Interface for failures that can be converted to HTTP responses.
///
/// Implementing this interface allows domain errors to customize their
/// HTTP representation, including status codes, response bodies, and
/// OpenAPI schema documentation.
///
/// # Features
/// - Custom ProblemDetail generation
/// - OpenAPI schema annotations support
/// - Validation error formatting
///
/// # Usage
/// ```java
/// @ResponseStatus(HttpStatus.BAD_REQUEST)
/// record InvalidData(Map<String, String> errors) implements HttpFailure {
///   @Override
///   public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
///     return Optional.of(createValidationError(errors));
///   }
/// }
/// ```
public interface HttpFailure {

	/// Allows a failure to provide a customized ProblemDetail.
    ///
    /// If this returns empty, the system falls back to using the @ResponseStatus
    /// annotation on the error class.
    ///
    /// # Example
    /// ```java
  /// @Override
  /// public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
  ///   ProblemDetail pd = ProblemDetail.forStatusAndDetail(
  ///     HttpStatus.CONFLICT,
  ///     "Email already exists"
  ///   );
  ///   pd.setProperty("email", this.email);
  ///   return Optional.of(pd);
  /// }
  /// ```
    ///
    /// @param request The HTTP servlet request.
    /// @return Optional containing a custom ProblemDetail, or empty for default behavior.
	default Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
		return Optional.empty();
	}

	/// Creates a validation error response with field-level errors.
    ///
    /// Follows RFC 7807 Problem Details format with an additional "errors" property.
    ///
    /// # Response Format
    /// ```json
  /// {
  ///   "type": "about:blank",
  ///   "title": "Bad Request",
  ///   "status": 400,
  ///   "detail": "Validation failed",
  ///   "errors": {
  ///     "email": "Must be a valid email address",
  ///     "age": "Must be positive"
  ///   }
  /// }
  /// ```
    ///
    /// @param errors Map of field names to error messages.
    /// @return A ProblemDetail configured for validation errors.
	default ProblemDetail createValidationError(Map<String, String> errors) {
		final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
		pd.setProperty("errors", errors);
		return pd;
	}

	/// Creates a validation error response with additional metadata.
    ///
    /// Allows including extra context beyond just field errors.
    ///
    /// @param errors     Map of field names to error messages.
    /// @param extensions Additional properties to include.
    /// @return A ProblemDetail configured for validation errors with extensions.
	default ProblemDetail createValidationError(Map<String, String> errors, Map<String, Object> extensions) {
		final ProblemDetail pd = this.createValidationError(errors);
		extensions.forEach(pd::setProperty);
		return pd;
	}

	/// Creates a conflict error response.
    ///
    /// Used for errors like duplicate resources or concurrent modification failures.
    ///
    /// @param detail  The error detail message.
    /// @param resource The resource that caused the conflict.
    /// @return A ProblemDetail configured for conflict errors.
	default ProblemDetail createConflictError(String detail, String resource) {
		final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
		pd.setProperty("resource", resource);
		return pd;
	}

	/// Creates a not found error response.
    ///
    /// @param resourceType The type of resource not found.
    /// @param identifier   The identifier used in the search.
    /// @return A ProblemDetail configured for not found errors.
	default ProblemDetail createNotFoundError(String resourceType, Object identifier) {
		final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				String.format("%s not found with identifier: %s", resourceType, identifier));
		pd.setProperty("resourceType", resourceType);
		pd.setProperty("identifier", identifier);
		return pd;
	}

	/// Creates a business rule violation error response.
    ///
    /// Used for domain-specific constraint violations.
    ///
    /// @param rule        The business rule that was violated.
    /// @param description Explanation of the violation.
    /// @return A ProblemDetail configured for business rule errors.
	default ProblemDetail createBusinessRuleError(String rule, String description) {
		final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, description);
		pd.setProperty("rule", rule);
		return pd;
	}

	/// Creates a rate limit exceeded error response.
    ///
    /// @param limit      The rate limit that was exceeded.
    /// @param retryAfter Seconds until the client can retry.
    /// @return A ProblemDetail configured for rate limit errors.
	default ProblemDetail createRateLimitError(int limit, long retryAfter) {
		final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
		pd.setProperty("limit", limit);
		pd.setProperty("retryAfter", retryAfter);
		return pd;
	}
}
