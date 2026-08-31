package dev.jose.result.spring;

import dev.jose.result.Failure;
import dev.jose.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/// Unwraps controller `Result` responses and converts domain failures to
/// standard `ProblemDetail` responses.
///
/// Controller methods may return either `Result<T, E>` or
/// `ResponseEntity<Result<T, E>>`. An `Ok` is replaced by its value. An `Err`
/// whose value implements [Failure] is converted automatically using its
/// `@ResponseStatus` annotation, or HTTP 500 when no annotation is present.
///
/// Message keys use `error.<FailureSimpleName>` for detail and
/// `error.title.<FailureSimpleName>` for title. Missing messages fall back to
/// [Failure#getMessage()] and [Failure#getTitle()].
///
/// # Configuration ```java
/// @Bean ResultResponseAdvice resultResponseAdvice(MessageSource messageSource)
/// { return new ResultResponseAdvice(messageSource); } ```
@RestControllerAdvice
public class ResultResponseAdvice implements ResponseBodyAdvice<Object> {

  private final MessageSource messageSource;

  /// Creates the advice with the application's message source.
  ///
  /// @param messageSource source used to localize failure details and titles
  public ResultResponseAdvice(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /// Applies only to direct `Result` responses and `ResponseEntity` responses
  /// whose declared body type is `Result`.
  @Override
  public boolean supports(
      MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    final ResolvableType declaredType = ResolvableType.forMethodParameter(returnType);
    if (Result.class.isAssignableFrom(declaredType.toClass())) {
      return true;
    }

    if (!ResponseEntity.class.isAssignableFrom(declaredType.toClass())) {
      return false;
    }

    final Class<?> bodyType = declaredType.getGeneric(0).resolve();
    return bodyType != null && Result.class.isAssignableFrom(bodyType);
  }

  /// Unwraps an `Ok` or converts an `Err` to `ProblemDetail` before the body
  /// is serialized.
  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter returnType,
      MediaType selectedContentType,
      Class<? extends HttpMessageConverter<?>> selectedConverterType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (!(body instanceof Result<?, ?> result)) {
      return body;
    }

    return switch (result) {
      case Result.Ok(var value) -> value;
      case Result.Err(var error) when error instanceof Failure failure ->
          this.createProblemDetail(failure, request, response);
      case Result.Err(var error) ->
          throw new IllegalStateException(
              "Result.Err error must implement Failure, got: " + error.getClass().getName());
    };
  }

  private ProblemDetail createProblemDetail(
      Failure failure, ServerHttpRequest request, ServerHttpResponse response) {
    final HttpServletRequest servletRequest =
        ((ServletServerHttpRequest) request).getServletRequest();
    final Locale locale = servletRequest.getLocale();
    final ResponseStatus responseStatus =
        AnnotatedElementUtils.findMergedAnnotation(failure.getClass(), ResponseStatus.class);
    final HttpStatus status =
        responseStatus == null ? HttpStatus.INTERNAL_SERVER_ERROR : responseStatus.code();

    final String simpleName = failure.getClass().getSimpleName();
    final String detail =
        this.messageSource.getMessage(
            "error." + simpleName, failure.getMessageArgs(), failure.getMessage(), locale);
    final String title =
        this.messageSource.getMessage(
            "error.title." + simpleName, null, failure.getTitle(), locale);

    final ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setInstance(URI.create(servletRequest.getRequestURI()));
    failure.getExtensions().forEach(problemDetail::setProperty);
    problemDetail.setProperty("errorCode", failure.getErrorCode());

    response.setStatusCode(status);
    return problemDetail;
  }
}
