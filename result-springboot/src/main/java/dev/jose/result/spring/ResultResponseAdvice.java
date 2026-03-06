package dev.jose.result.spring;

import dev.jose.result.AsyncResult;
import dev.jose.result.Result;
import dev.jose.result.utils.BaseFailure;
import dev.jose.result.utils.HttpFailure;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/// Spring ResponseBodyAdvice that automatically unwraps Result and AsyncResult types into HTTP responses.
///
/// This advice intercepts controller methods returning Result, AsyncResult, or ResponseEntity wrappers
/// and converts them to appropriate HTTP responses with proper status codes and Problem Details for errors.
///
/// # Features
/// - Automatic Result and AsyncResult unwrapping
/// - RFC 7807 Problem Details support
/// - i18n error messages
/// - Metrics/observability integration
/// - Comprehensive error logging
/// - Fallback exception handling
///
/// # Configuration
/// ```java
/// @Configuration
/// public class ResultConfig {
///   @Bean
///   public ResultResponseAdvice resultAdvice(
///       MessageSource messageSource,
///       MeterRegistry meterRegistry
///   ) {
///     return new ResultResponseAdvice(messageSource, meterRegistry);
///   }
/// }
/// ```
@RestControllerAdvice
public class ResultResponseAdvice implements ResponseBodyAdvice<Object> {

	private static final Logger log = LoggerFactory.getLogger(ResultResponseAdvice.class);

	private final MessageSource messageSource;

	private final MeterRegistry meterRegistry;

	public ResultResponseAdvice(MessageSource messageSource, MeterRegistry meterRegistry) {
		this.messageSource = messageSource;
		this.meterRegistry = meterRegistry;
	}

	/// Determines if this advice should be applied.
    ///
    /// Applies to methods returning Result, AsyncResult, or ResponseEntity.
	@Override
	public boolean supports(MethodParameter returnType,
			@NonNull Class<? extends HttpMessageConverter<?>> converterType) {
		final Class<?> paramType = returnType.getParameterType();
		return Result.class.isAssignableFrom(paramType) || AsyncResult.class.isAssignableFrom(paramType)
				|| ResponseEntity.class.isAssignableFrom(paramType);
	}

	/// Processes the response body before writing.
    ///
    /// Unwraps Result and AsyncResult types, converting failures to Problem Details.
	@Override
	public Object beforeBodyWrite(Object body, @NonNull MethodParameter returnType,
			@NonNull MediaType selectedContentType,
			@NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType, @NonNull ServerHttpRequest request,
			@NonNull ServerHttpResponse response) {

		// Unwrap AsyncResult by blocking (for servlet stack)
		if (body instanceof AsyncResult<?, ?> asyncResult) {
			body = asyncResult.join();
		}

		if (body instanceof Result<?, ?> result) {
			final var resultBody = this.handleResult(result, request, response);
			log.debug("Response: {}", resultBody);
			return resultBody;
		}

		log.debug("Response: {}", body);
		return body;
	}

	/// Handles Result instances.
    ///
    /// @param result   The Result to process.
    /// @param request  The HTTP request.
    /// @param response The HTTP response.
    /// @return The unwrapped success value or a ProblemDetail.
	private Object handleResult(Result<?, ?> result, ServerHttpRequest request, ServerHttpResponse response) {
		return switch (result) {
			case Result.Success(var val) -> this.handleSuccess(val, response);
			case Result.Failure(var err) when err instanceof BaseFailure bf ->
				this.handleFailure(bf, request, response);
			case Result.Failure(var err) -> {
				log.error("Result.Failure with non-BaseFailure error type: {}", err.getClass());
				throw new IllegalStateException("Failure must extend BaseFailure, got: " + err.getClass());
			}
		};
	}

	/// Handles successful results.
    ///
    /// @param value    The success value.
    /// @param response The HTTP response.
    /// @return The value to be serialized.
	private Object handleSuccess(Object value, ServerHttpResponse response) {
		if (value instanceof ResponseEntity<?> entity) {
			response.setStatusCode(entity.getStatusCode());
			response.getHeaders().addAll(entity.getHeaders());
			return entity.getBody();
		}
		return value;
	}

	/// Handles failure results.
    ///
    /// @param error    The BaseFailure error.
    /// @param request  The HTTP request.
    /// @param response The HTTP response.
    /// @return A ProblemDetail for the error.
	private ProblemDetail handleFailure(BaseFailure error, ServerHttpRequest request, ServerHttpResponse response) {
		final HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
		final Locale locale = servletRequest.getLocale();

		// Log the error if configured
		this.logError(error);

		// Record metrics
		this.recordErrorMetric(error);

		// Resolve the ProblemDetail
		final ProblemDetail pd = this.resolveProblemDetail(error, servletRequest, locale);

		// Set response status
		response.setStatusCode(HttpStatusCode.valueOf(pd.getStatus()));

		return pd;
	}

	/// Resolves the ProblemDetail for a given error.
    ///
    /// @param error   The BaseFailure to convert.
    /// @param request The servlet request.
    /// @param locale  The request locale.
    /// @return The configured ProblemDetail.
	private ProblemDetail resolveProblemDetail(BaseFailure error, HttpServletRequest request, Locale locale) {
		// Try custom ProblemDetail if HttpFailure
		if (error instanceof HttpFailure hf) {
			final Optional<ProblemDetail> custom = hf.toProblemDetail(request);
			if (custom.isPresent()) {
				final ProblemDetail pd = custom.get();
				this.enrichProblemDetail(pd, error, request, locale);
				return pd;
			}
		}

		// Fall back to @ResponseStatus
		final ResponseStatus statusAnno = AnnotatedElementUtils.findMergedAnnotation(error.getClass(),
				ResponseStatus.class);
		final HttpStatus status = (statusAnno != null) ? statusAnno.value() : HttpStatus.INTERNAL_SERVER_ERROR;

		final String detail = error.getMessage(locale);

		final ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
		this.enrichProblemDetail(pd, error, request, locale);

		return pd;
	}

	/// Enriches a ProblemDetail with additional metadata.
    ///
    /// @param pd      The ProblemDetail to enrich.
    /// @param error   The source error.
    /// @param request The servlet request.
    /// @param locale  The request locale.
	private void enrichProblemDetail(ProblemDetail pd, BaseFailure error, HttpServletRequest request, Locale locale) {
		// Set title if not already set
		if (pd.getTitle() == null) {
			final String title = this.resolveTitle(error, locale);
			pd.setTitle(title);
		}

		// Set instance
		pd.setInstance(URI.create(request.getRequestURI()));

		// Add error code
		pd.setProperty("errorCode", error.getErrorCode());

		// Add custom extensions
		error.getExtensions().forEach(pd::setProperty);
	}

	/// Resolves the error title with i18n support.
    ///
    /// @param error  The error.
    /// @param locale The locale.
    /// @return The localized title.
	private String resolveTitle(BaseFailure error, Locale locale) {
		try {
			return this.messageSource.getMessage("error.title." + error.getClass().getSimpleName(), null,
					error.getTitle(), locale);
		} catch (final Exception _) {
			return error.getTitle();
		}
	}

	/// Logs the error if configured.
    ///
    /// @param error The error to log.
	private void logError(BaseFailure error) {
		if (!error.shouldLog()) {
			return;
		}

		final String level = error.getLogLevel();
		final String message = String.format("Domain error occurred: %s - %s", error.getClass().getSimpleName(),
				error.getMessage());

		switch (level.toUpperCase()) {
			case "ERROR" -> log.error(message);
			case "INFO" -> log.info(message);
			case "DEBUG" -> log.debug(message);
			default -> log.warn(message);
		}
	}

	/// Records error metrics.
    ///
    /// @param error The error to record.
	private void recordErrorMetric(BaseFailure error) {
		if (this.meterRegistry != null) {
			Counter.builder("api.errors").tag("type", error.getClass().getSimpleName())
					.tag("code", error.getErrorCode()).description("Count of API errors by type")
					.register(this.meterRegistry).increment();
		}
	}

	/// Global exception handler for non-Result exceptions.
    ///
    /// This provides a safety net for exceptions that escape Result wrapping.
    ///
    /// @param ex      The exception.
    /// @param request The HTTP request.
    /// @return A ProblemDetail for the exception.
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpectedException(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception in controller", ex);

		if (this.meterRegistry != null) {
			Counter.builder("api.errors.unhandled").tag("exception", ex.getClass().getSimpleName())
					.description("Count of unhandled exceptions").register(this.meterRegistry).increment();
		}

		final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred");
		pd.setTitle("Internal Server Error");
		pd.setInstance(URI.create(request.getRequestURI()));
		pd.setProperty("errorCode", "INTERNAL_ERROR");

		return pd;
	}
}
