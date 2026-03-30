package dev.jose.result.spring;

import dev.jose.result.AsyncResult;
import dev.jose.result.Result;
import dev.jose.result.utils.BaseFailure;
import dev.jose.result.utils.HttpFailure;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({ResultResponseAdviceTest.TestConfig.class, ResultResponseAdviceTest.TestController.class})
class ResultResponseAdviceTest {

	@Configuration
	static class TestConfig {
		@Bean
		public MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}

		@Bean
		public ResultResponseAdvice resultResponseAdvice(MessageSource messageSource, MeterRegistry meterRegistry) {
			return new ResultResponseAdvice(messageSource, meterRegistry);
		}
	}

	@RestController
	static class TestController {

		@GetMapping("/success")
		public Result<UserDto, TestError> success() {
			return Result.success(new UserDto("1", "John"));
		}

		@GetMapping("/success-null")
		public Result<String, TestError> successNull() {
			return Result.success(null);
		}

		@GetMapping("/failure")
		public Result<UserDto, TestError> failure() {
			return Result.failure(new TestError.NotFound("123"));
		}

		@GetMapping("/failure-not-found")
		public Result<UserDto, TestError> failureNotFound() {
			return Result.failure(new TestError.NotFoundHttp("123"));
		}

		@GetMapping("/failure-bad-request")
		public Result<UserDto, TestError> failureBadRequest() {
			return Result.failure(new TestError.BadRequest("Invalid input"));
		}

		@GetMapping("/failure-validation")
		public Result<UserDto, TestError> failureValidation() {
			return Result.failure(new TestError.ValidationError(Map.of("email", "Invalid", "age", "Required")));
		}

		@GetMapping("/failure-custom-pd")
		public Result<UserDto, TestError> failureCustomPd() {
			return Result.failure(new TestError.CustomPdError("custom"));
		}

		@GetMapping("/failure-no-response-status")
		public Result<UserDto, TestError> failureNoResponseStatus() {
			return Result.failure(new TestError.NoAnnotationError("error"));
		}

		@GetMapping("/async-success")
		public AsyncResult<UserDto, TestError> asyncSuccess() {
			return AsyncResult.success(new UserDto("1", "Jane"));
		}

		@GetMapping("/async-failure")
		public AsyncResult<UserDto, TestError> asyncFailure() {
			return AsyncResult.failure(new TestError.NotFound("456"));
		}

		@GetMapping("/response-entity-success")
		public ResponseEntity<UserDto> responseEntitySuccess() {
			return ResponseEntity.ok(new UserDto("1", "Bob"));
		}

		@GetMapping("/response-entity-with-headers")
		public ResponseEntity<UserDto> responseEntityWithHeaders() {
			return ResponseEntity.status(HttpStatus.CREATED).header("X-Custom", "value")
					.body(new UserDto("1", "Alice"));
		}

		@GetMapping("/plain-string")
		public String plainString() {
			return "hello";
		}

		@GetMapping("/plain-object")
		public PlainDto plainObject() {
			return new PlainDto("data");
		}

		@GetMapping("/throw-exception")
		public Result<UserDto, TestError> throwException() {
			throw new RuntimeException("Unhandled exception");
		}

		@GetMapping("/failure-forbidden")
		public Result<UserDto, TestError> failureForbidden() {
			return Result.failure(new TestError.ForbiddenError("Access denied"));
		}

		@GetMapping("/failure-with-extensions")
		public Result<UserDto, TestError> failureWithExtensions() {
			return Result.failure(new TestError.ExtensionsError("error"));
		}

		@GetMapping("/failure-info-level")
		public Result<UserDto, TestError> failureInfoLevel() {
			return Result.failure(new TestError.InfoLevelError("info"));
		}

		@GetMapping("/failure-warn-level")
		public Result<UserDto, TestError> failureWarnLevel() {
			return Result.failure(new TestError.WarnLevelError("warn"));
		}

		@GetMapping("/failure-debug-level")
		public Result<UserDto, TestError> failureDebugLevel() {
			return Result.failure(new TestError.DebugLevelError("debug"));
		}
	}

	record UserDto(String id, String name) {
	}

	record PlainDto(String data) {
	}

	sealed interface TestError permits TestError.NotFound, TestError.NotFoundHttp, TestError.BadRequest,
			TestError.ValidationError, TestError.CustomPdError, TestError.NoAnnotationError, TestError.ForbiddenError,
			TestError.ExtensionsError, TestError.InfoLevelError, TestError.WarnLevelError, TestError.DebugLevelError {
		record NotFound(String id) implements TestError, BaseFailure, HttpFailure {
			@Override
			public String getMessage() {
				return "User not found: " + this.id;
			}

			@Override
			public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
				final var pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, this.getMessage());
				return Optional.of(pd);
			}
		}

		@ResponseStatus(HttpStatus.NOT_FOUND)
		record NotFoundHttp(String id) implements TestError, BaseFailure {
			@Override
			public String getMessage() {
				return "User not found: " + this.id;
			}
		}

		record BadRequest(String message) implements TestError, BaseFailure, HttpFailure {
			@Override
			public String getMessage() {
				return this.message;
			}

			@Override
			public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
				final var pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, this.getMessage());
				pd.setProperty("field", "request");
				return Optional.of(pd);
			}
		}

		record ValidationError(Map<String, String> errors) implements TestError, BaseFailure, HttpFailure {
			@Override
			public String getMessage() {
				return "Validation failed";
			}

			@Override
			public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
				return Optional.of(this.createValidationError(this.errors()));
			}
		}

		record CustomPdError(String value) implements TestError, BaseFailure, HttpFailure {
			@Override
			public String getMessage() {
				return "Custom error";
			}

			@Override
			public Optional<ProblemDetail> toProblemDetail(HttpServletRequest request) {
				final var pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Custom detail");
				pd.setTitle("CustomTitle");
				return Optional.of(pd);
			}
		}

		record NoAnnotationError(String msg) implements TestError, BaseFailure {
			@Override
			public String getMessage() {
				return this.msg;
			}
		}

		@ResponseStatus(HttpStatus.FORBIDDEN)
		record ForbiddenError(String msg) implements TestError, BaseFailure {
			@Override
			public String getMessage() {
				return this.msg;
			}
		}

		record ExtensionsError(String msg) implements TestError, BaseFailure {
			@Override
			public String getMessage() {
				return this.msg;
			}

			@Override
			public Map<String, Object> getExtensions() {
				return Map.of("retryCount", 2, "severity", "medium");
			}
		}

		record InfoLevelError(String msg) implements TestError, BaseFailure {
			@Override
			public String getMessage() {
				return this.msg;
			}

			@Override
			public String getLogLevel() {
				return "INFO";
			}
		}

		record WarnLevelError(String msg) implements TestError, BaseFailure {
			@Override
			public String getMessage() {
				return this.msg;
			}

		}

		record DebugLevelError(String msg) implements TestError, BaseFailure {
			@Override
			public String getMessage() {
				return this.msg;
			}

			@Override
			public String getLogLevel() {
				return "DEBUG";
			}
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MeterRegistry meterRegistry;

	@Test
	@DisplayName("supports(): returns true for Result return type")
	void supportsResult() throws Exception {
		this.mockMvc.perform(get("/success")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("supports(): returns true for AsyncResult return type")
	void supportsAsyncResult() throws Exception {
		this.mockMvc.perform(get("/async-success")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("supports(): returns true for ResponseEntity return type")
	void supportsResponseEntity() throws Exception {
		this.mockMvc.perform(get("/response-entity-success")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("supports(): returns false for String return type (passes through)")
	void rejectsString() throws Exception {
		this.mockMvc.perform(get("/plain-string")).andExpect(status().isOk()).andExpect(content().string("hello"));
	}

	@Test
	@DisplayName("supports(): returns false for plain DTO return type")
	void rejectsPlainDto() throws Exception {
		this.mockMvc.perform(get("/plain-object")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data").value("data"));
	}

	@Test
	@DisplayName("Result.Success: returns value as-is for plain DTO")
	void successReturnsPlainDto() throws Exception {
		this.mockMvc.perform(get("/success")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("1"))
				.andExpect(jsonPath("$.name").value("John"));
	}

	@Test
	@DisplayName("Result.Success: returns null value correctly")
	void successReturnsNullValue() throws Exception {
		this.mockMvc.perform(get("/success-null")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("Result.Success: ResponseEntity sets status code")
	void responseEntitySetsStatus() throws Exception {
		this.mockMvc.perform(get("/response-entity-with-headers")).andExpect(status().isCreated())
				.andExpect(header().string("X-Custom", "value")).andExpect(jsonPath("$.name").value("Alice"));
	}

	@Test
	@DisplayName("Result.Failure: HttpFailure with custom ProblemDetail returns custom status and properties")
	void failureCustomProblemDetail() throws Exception {
		this.mockMvc.perform(get("/failure-custom-pd")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("CustomTitle"))
				.andExpect(jsonPath("$.detail").value("Custom detail"))
				.andExpect(jsonPath("$.errorCode").value("CUSTOM_PD_ERROR"));
	}

	@Test
	@DisplayName("Result.Failure: HttpFailure enriches ProblemDetail with extensions")
	void failureEnrichesCustomProblemDetail() throws Exception {
		this.mockMvc.perform(get("/failure-bad-request")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.field").value("request"));
	}

	@Test
	@DisplayName("Result.Failure: HttpFailure validation error includes errors map")
	void failureValidationErrorWithMap() throws Exception {
		this.mockMvc.perform(get("/failure-validation")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.email").value("Invalid"))
				.andExpect(jsonPath("$.errors.age").value("Required"));
	}

	@Test
	@DisplayName("Result.Failure: Non-HttpFailure uses @ResponseStatus when present")
	void failureUsesResponseStatusAnnotation() throws Exception {
		this.mockMvc.perform(get("/failure-bad-request")).andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Result.Failure: Non-HttpFailure without @ResponseStatus defaults to 500")
	void failureDefaultsTo500() throws Exception {
		this.mockMvc.perform(get("/failure-no-response-status")).andExpect(status().isInternalServerError());
	}

	@Test
	@DisplayName("Result.Failure: sets errorCode property")
	void failureSetsErrorCode() throws Exception {
		this.mockMvc.perform(get("/failure")).andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("Result.Failure: sets instance to request URI")
	void failureSetsInstance() throws Exception {
		this.mockMvc.perform(get("/failure")).andExpect(jsonPath("$.instance").value("/failure"));
	}

	@Test
	@DisplayName("AsyncResult: Success unwraps to value")
	void asyncSuccessUnwraps() throws Exception {
		this.mockMvc.perform(get("/async-success")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("1"))
				.andExpect(jsonPath("$.name").value("Jane"));
	}

	@Test
	@DisplayName("AsyncResult: Failure unwraps to ProblemDetail")
	void asyncFailureUnwraps() throws Exception {
		this.mockMvc.perform(get("/async-failure")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("logError(): does not log when shouldLog is false")
	void logErrorSkipsWhenShouldLogFalse() throws Exception {
		this.mockMvc.perform(get("/failure")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("recordErrorMetric(): increments error counter with tags")
	void recordErrorMetricIncrementsCounter() throws Exception {
		this.mockMvc.perform(get("/failure")).andExpect(status().isNotFound());

		final Counter counter = this.meterRegistry.counter("api.errors", "type", "NotFound", "code", "NOT_FOUND");
		assertNotNull(counter);
		assertTrue(counter.count() >= 1.0, "Counter should be at least 1.0, was: " + counter.count());
	}

	@Test
	@DisplayName("Edge case: non-Result type passes through unchanged")
	void edgeCaseNonResultPassesThrough() throws Exception {
		this.mockMvc.perform(get("/plain-string")).andExpect(status().isOk()).andExpect(content().string("hello"));
	}

	@Test
	@DisplayName("Edge case: non-Result DTO passes through as JSON")
	void edgeCaseNonResultDtoPassesThrough() throws Exception {
		this.mockMvc.perform(get("/plain-object")).andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.data").value("data"));
	}

	@Test
	@DisplayName("handleUnexpectedException(): returns ProblemDetail for unhandled exceptions")
	void handleUnexpectedExceptionReturnsProblemDetail() throws Exception {
		this.mockMvc.perform(get("/throw-exception")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.title").value("Internal Server Error"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
				.andExpect(jsonPath("$.instance").value("/throw-exception"))
				.andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));

		final Counter counter = this.meterRegistry.counter("api.errors.unhandled", "exception", "RuntimeException");
		assertEquals(1.0, counter.count(), 0.001);
	}

	@Test
	@DisplayName("Result.Failure: @ResponseStatus annotation sets correct HTTP status")
	void failureWithResponseStatusAnnotation() throws Exception {
		this.mockMvc.perform(get("/failure-forbidden")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("Result.Failure: enrichProblemDetail adds custom extensions to ProblemDetail")
	void enrichProblemDetailAddsExtensions() throws Exception {
		this.mockMvc.perform(get("/failure-with-extensions")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("EXTENSIONS_ERROR"))
				.andExpect(jsonPath("$.retryCount").value(2)).andExpect(jsonPath("$.severity").value("medium"));
	}

	@Test
	@DisplayName("Result.Failure: enrichProblemDetail does not override existing title")
	void enrichProblemDetailDoesNotOverrideTitle() throws Exception {
		this.mockMvc.perform(get("/failure-custom-pd")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.title").value("CustomTitle"));
	}

	@Test
	@DisplayName("logError(): logs at INFO level when getLogLevel returns INFO")
	void logErrorAtInfoLevel() throws Exception {
		this.mockMvc.perform(get("/failure-info-level")).andExpect(status().isInternalServerError());
	}

	@Test
	@DisplayName("logError(): logs at WARN level when getLogLevel returns WARN")
	void logErrorAtWarnLevel() throws Exception {
		this.mockMvc.perform(get("/failure-warn-level")).andExpect(status().isInternalServerError());
	}

	@Test
	@DisplayName("logError(): logs at DEBUG level when getLogLevel returns DEBUG")
	void logErrorAtDebugLevel() throws Exception {
		this.mockMvc.perform(get("/failure-debug-level")).andExpect(status().isInternalServerError());
	}

	@Test
	@DisplayName("resolveProblemDetail: falls back to @ResponseStatus when HttpFailure.toProblemDetail returns empty")
	void resolveProblemDetailFallsBackToResponseStatus() throws Exception {
		this.mockMvc.perform(get("/failure-forbidden")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.detail").value("Access denied"))
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN_ERROR"));
	}
}
