package dev.jose.result.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HttpFailureTest {

	record SimpleHttpFailure() implements HttpFailure {
	}

	record CustomProblemDetailFailure() implements HttpFailure {
		@Override
		public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
			final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Custom detail");
			pd.setProperty("customField", "customValue");
			return Optional.of(pd);
		}
	}

	@Nested
	@DisplayName("toProblemDetail()")
	class ToProblemDetailTests {

		@Test
		@DisplayName("default returns Optional.empty()")
		void defaultReturnsEmpty() {
			final var failure = new SimpleHttpFailure();
			final var request = mock(HttpServletRequest.class);
			assertTrue(failure.toProblemDetail(request).isEmpty());
		}

		@Test
		@DisplayName("returns custom ProblemDetail when overridden")
		void returnsCustomProblemDetail() {
			final var failure = new CustomProblemDetailFailure();
			final var request = mock(HttpServletRequest.class);
			final Optional<ProblemDetail> result = failure.toProblemDetail(request);
			assertTrue(result.isPresent());
			assertEquals(HttpStatus.CONFLICT.value(), result.get().getStatus());
			assertEquals("Custom detail", result.get().getDetail());
			assertNotNull(result.get().getProperties());
			assertEquals("customValue", result.get().getProperties().get("customField"));
		}
	}

	@Nested
	@DisplayName("createValidationError(Map)")
	class CreateValidationErrorTests {

		@Test
		@DisplayName("creates ProblemDetail with status 400")
		void createsBadRequestStatus() {
			final Map<String, String> errors = Map.of("email", "Invalid email", "age", "Must be positive");
			final ProblemDetail pd = HttpFailureTest.this.createValidationError(errors);

			assertEquals(HttpStatus.BAD_REQUEST.value(), pd.getStatus());
		}

		@Test
		@DisplayName("sets detail to 'Validation failed'")
		void setsDetailMessage() {
			final Map<String, String> errors = Map.of("field", "error");
			final ProblemDetail pd = HttpFailureTest.this.createValidationError(errors);

			assertEquals("Validation failed", pd.getDetail());
		}

		@Test
		@DisplayName("adds errors property")
		void addsErrorsProperty() {
			final Map<String, String> errors = Map.of("email", "Invalid", "age", "Must be positive");
			final ProblemDetail pd = HttpFailureTest.this.createValidationError(errors);

			assertNotNull(pd.getProperties());
			@SuppressWarnings("unchecked")
			final Map<String, Object> errorsProp = (Map<String, Object>) pd.getProperties().get("errors");
			assertEquals("Invalid", errorsProp.get("email"));
			assertEquals("Must be positive", errorsProp.get("age"));
		}
	}

	@Nested
	@DisplayName("createValidationError(Map, Map)")
	class CreateValidationErrorWithExtensionsTests {

		@Test
		@DisplayName("adds custom extensions")
		void addsCustomExtensions() {
			final Map<String, String> errors = Map.of("field", "error");
			final Map<String, Object> extensions = Map.of("timestamp", 1234567890L, "source", "validator");
			final ProblemDetail pd = HttpFailureTest.this.createValidationError(errors, extensions);

			assertNotNull(pd.getProperties());
			assertEquals(1234567890L, pd.getProperties().get("timestamp"));
			assertEquals("validator", pd.getProperties().get("source"));
		}

		@Test
		@DisplayName("includes base errors property plus extensions")
		void includesErrorsAndExtensions() {
			final Map<String, String> errors = Map.of("field", "error");
			final Map<String, Object> extensions = Map.of("extra", "value");
			final ProblemDetail pd = HttpFailureTest.this.createValidationError(errors, extensions);

			assertNotNull(pd.getProperties());
			assertNotNull(pd.getProperties().get("errors"));
			assertEquals("value", pd.getProperties().get("extra"));
		}
	}

	@Nested
	@DisplayName("createConflictError()")
	class CreateConflictErrorTests {

		@Test
		@DisplayName("creates ProblemDetail with status 409")
		void createsConflictStatus() {
			final ProblemDetail pd = HttpFailureTest.this.createConflictError("Resource already exists", "User");

			assertEquals(HttpStatus.CONFLICT.value(), pd.getStatus());
		}

		@Test
		@DisplayName("sets detail message")
		void setsDetail() {
			final ProblemDetail pd = HttpFailureTest.this.createConflictError("Duplicate email", "Email");

			assertEquals("Duplicate email", pd.getDetail());
		}

		@Test
		@DisplayName("adds resource property")
		void addsResourceProperty() {
			final ProblemDetail pd = HttpFailureTest.this.createConflictError("conflict", "User");

			assertNotNull(pd.getProperties());
			assertEquals("User", pd.getProperties().get("resource"));
		}
	}

	@Nested
	@DisplayName("createNotFoundError()")
	class CreateNotFoundErrorTests {

		@Test
		@DisplayName("creates ProblemDetail with status 404")
		void createsNotFoundStatus() {
			final ProblemDetail pd = HttpFailureTest.this.createNotFoundError("User", "123");

			assertEquals(HttpStatus.NOT_FOUND.value(), pd.getStatus());
		}

		@Test
		@DisplayName("sets detail with resource and identifier")
		void setsDetail() {
			final ProblemDetail pd = HttpFailureTest.this.createNotFoundError("Order", "ORD-456");

			assertNotNull(pd.getDetail());
			assertTrue(pd.getDetail().contains("Order"));
			assertTrue(pd.getDetail().contains("ORD-456"));
		}

		@Test
		@DisplayName("adds resourceType and identifier properties")
		void addsProperties() {
			final ProblemDetail pd = HttpFailureTest.this.createNotFoundError("Product", "prod-789");

			assertNotNull(pd.getProperties());
			assertEquals("Product", pd.getProperties().get("resourceType"));
			assertEquals("prod-789", pd.getProperties().get("identifier"));
		}
	}

	@Nested
	@DisplayName("createBusinessRuleError()")
	class CreateBusinessRuleErrorTests {

		@Test
		@DisplayName("creates ProblemDetail with status 422")
		void createsUnprocessableStatus() {
			final ProblemDetail pd = HttpFailureTest.this.createBusinessRuleError("minAge", "Must be 18 or older");

			assertEquals(HttpStatus.UNPROCESSABLE_CONTENT.value(), pd.getStatus());
		}

		@Test
		@DisplayName("sets detail message")
		void setsDetail() {
			final ProblemDetail pd = HttpFailureTest.this.createBusinessRuleError("rule", "description");

			assertEquals("description", pd.getDetail());
		}

		@Test
		@DisplayName("adds rule property")
		void addsRuleProperty() {
			final ProblemDetail pd = HttpFailureTest.this.createBusinessRuleError("uniqueEmail", "desc");

			assertNotNull(pd.getProperties());
			assertEquals("uniqueEmail", pd.getProperties().get("rule"));
		}
	}

	@Nested
	@DisplayName("createRateLimitError()")
	class CreateRateLimitErrorTests {

		@Test
		@DisplayName("creates ProblemDetail with status 429")
		void createsTooManyRequestsStatus() {
			final ProblemDetail pd = HttpFailureTest.this.createRateLimitError(100, 60);

			assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), pd.getStatus());
		}

		@Test
		@DisplayName("sets detail message")
		void setsDetail() {
			final ProblemDetail pd = HttpFailureTest.this.createRateLimitError(10, 30);

			assertEquals("Rate limit exceeded", pd.getDetail());
		}

		@Test
		@DisplayName("adds limit property")
		void addsLimitProperty() {
			final ProblemDetail pd = HttpFailureTest.this.createRateLimitError(50, 120);

			assertNotNull(pd.getProperties());
			assertEquals(50, pd.getProperties().get("limit"));
		}

		@Test
		@DisplayName("adds retryAfter property")
		void addsRetryAfterProperty() {
			final ProblemDetail pd = HttpFailureTest.this.createRateLimitError(100, 45);

			assertNotNull(pd.getProperties());
			assertEquals(45L, pd.getProperties().get("retryAfter"));
		}
	}

	private ProblemDetail createValidationError(Map<String, String> errors) {
		return new HttpFailure() {
		}.createValidationError(errors);
	}

	private ProblemDetail createValidationError(Map<String, String> errors, Map<String, Object> extensions) {
		return new HttpFailure() {
		}.createValidationError(errors, extensions);
	}

	private ProblemDetail createConflictError(String detail, String resource) {
		return new HttpFailure() {
		}.createConflictError(detail, resource);
	}

	private ProblemDetail createNotFoundError(String resourceType, Object identifier) {
		return new HttpFailure() {
		}.createNotFoundError(resourceType, identifier);
	}

	private ProblemDetail createBusinessRuleError(String rule, String description) {
		return new HttpFailure() {
		}.createBusinessRuleError(rule, description);
	}

	private ProblemDetail createRateLimitError(int limit, long retryAfter) {
		return new HttpFailure() {
		}.createRateLimitError(limit, retryAfter);
	}
}
