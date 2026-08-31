package dev.jose.result.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.jose.result.Result;
import dev.jose.result.utils.Failure;
import jakarta.servlet.ServletException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import({ResultResponseAdviceTest.TestConfig.class, ResultResponseAdviceTest.TestController.class})
class ResultResponseAdviceTest {

  @Configuration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    MessageSource messageSource() {
      final StaticMessageSource messages = new StaticMessageSource();
      messages.addMessage("error.LocalizedFailure", Locale.FRENCH, "Le compte {0} est introuvable");
      messages.addMessage("error.title.LocalizedFailure", Locale.FRENCH, "Compte introuvable");
      return messages;
    }

    @Bean
    ResultResponseAdvice resultResponseAdvice(MessageSource messageSource) {
      return new ResultResponseAdvice(messageSource);
    }
  }

  @RestController
  static class TestController {

    @GetMapping("/ok")
    Result<UserDto, ApiFailure> ok() {
      return Result.ok(new UserDto("1", "Ada"));
    }

    @GetMapping("/not-found/{id}")
    Result<UserDto, ApiFailure> notFound(@PathVariable("id") long id) {
      return Result.err(new NotFound(id));
    }

    @GetMapping("/default-failure")
    Result<UserDto, ApiFailure> defaultFailure() {
      return Result.err(new Unavailable());
    }

    @GetMapping("/localized/{accountId}")
    Result<UserDto, ApiFailure> localized(@PathVariable("accountId") String accountId) {
      return Result.err(new LocalizedFailure(accountId));
    }

    @GetMapping("/validation")
    Result<UserDto, ApiFailure> validation() {
      return Result.err(new ValidationFailed(Map.of("email", "must be valid")));
    }

    @GetMapping("/result-response")
    ResponseEntity<Result<UserDto, ApiFailure>> resultResponse() {
      return ResponseEntity.status(HttpStatus.CREATED)
          .header("X-Custom", "value")
          .body(Result.ok(new UserDto("2", "Grace")));
    }

    @GetMapping("/result-response-error")
    ResponseEntity<Result<UserDto, ApiFailure>> resultResponseError() {
      return ResponseEntity.accepted()
          .header("X-Custom", "value")
          .body(Result.err(new NotFound(99)));
    }

    @GetMapping("/plain-response")
    ResponseEntity<UserDto> plainResponse() {
      return ResponseEntity.accepted().body(new UserDto("3", "Linus"));
    }

    @GetMapping("/plain-object")
    UserDto plainObject() {
      return new UserDto("4", "Margaret");
    }

    @GetMapping("/non-failure")
    Result<UserDto, String> nonFailure() {
      return Result.err("raw failure");
    }

    @GetMapping("/ordinary-exception")
    Result<UserDto, ApiFailure> ordinaryException() {
      throw new IllegalArgumentException("ordinary failure");
    }
  }

  record UserDto(String id, String name) {}

  sealed interface ApiFailure extends Failure
      permits NotFound, Unavailable, LocalizedFailure, ValidationFailed {}

  @ResponseStatus(HttpStatus.NOT_FOUND)
  record NotFound(long userId) implements ApiFailure {
    @Override
    public String getMessage() {
      return "User " + this.userId + " was not found";
    }

    @Override
    public String getTitle() {
      return "User Not Found";
    }

    @Override
    public Map<String, Object> getExtensions() {
      return Map.of("userId", this.userId);
    }
  }

  record Unavailable() implements ApiFailure {
    @Override
    public String getMessage() {
      return "Service is unavailable";
    }
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  record LocalizedFailure(String accountId) implements ApiFailure {
    @Override
    public String getMessage() {
      return "Account " + this.accountId + " was not found";
    }

    @Override
    public Object[] getMessageArgs() {
      return new Object[] {this.accountId};
    }
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  record ValidationFailed(Map<String, String> errors) implements ApiFailure {
    @Override
    public String getMessage() {
      return "Validation failed";
    }

    @Override
    public Map<String, Object> getExtensions() {
      return Map.of("errors", this.errors);
    }
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private ResultResponseAdvice advice;

  @Test
  void supportsOnlyResultRelatedReturnTypes() throws Exception {
    assertTrue(this.advice.supports(returnType("ok"), JacksonJsonHttpMessageConverter.class));
    assertTrue(
        this.advice.supports(returnType("resultResponse"), JacksonJsonHttpMessageConverter.class));
    assertFalse(
        this.advice.supports(returnType("plainResponse"), JacksonJsonHttpMessageConverter.class));
    assertFalse(
        this.advice.supports(returnType("plainObject"), JacksonJsonHttpMessageConverter.class));
  }

  @Test
  void unwrapsOkValue() throws Exception {
    this.mockMvc
        .perform(get("/ok"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("1"))
        .andExpect(jsonPath("$.name").value("Ada"));
  }

  @Test
  void unwrapsResultInsideResponseEntityWithoutTargetingUnrelatedResponseEntities()
      throws Exception {
    this.mockMvc
        .perform(get("/result-response"))
        .andExpect(status().isCreated())
        .andExpect(header().string("X-Custom", "value"))
        .andExpect(jsonPath("$.name").value("Grace"));

    this.mockMvc
        .perform(get("/plain-response"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.name").value("Linus"));
  }

  @Test
  void convertsErrInsideResponseEntityAndPreservesItsHeaders() throws Exception {
    this.mockMvc
        .perform(get("/result-response-error"))
        .andExpect(status().isNotFound())
        .andExpect(header().string("X-Custom", "value"))
        .andExpect(jsonPath("$.detail").value("User 99 was not found"));
  }

  @Test
  void convertsErrUsingResponseStatusAndAutomaticFields() throws Exception {
    this.mockMvc
        .perform(get("/not-found/42"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("User Not Found"))
        .andExpect(jsonPath("$.detail").value("User 42 was not found"))
        .andExpect(jsonPath("$.instance").value("/not-found/42"))
        .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
        .andExpect(jsonPath("$.userId").value(42));
  }

  @Test
  void defaultsToInternalServerErrorWithoutResponseStatus() throws Exception {
    this.mockMvc
        .perform(get("/default-failure"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.detail").value("Service is unavailable"))
        .andExpect(jsonPath("$.errorCode").value("UNAVAILABLE"));
  }

  @Test
  void localizesDetailAndTitleWithMessageArguments() throws Exception {
    this.mockMvc
        .perform(get("/localized/acct-7").header("Accept-Language", "fr"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Compte introuvable"))
        .andExpect(jsonPath("$.detail").value("Le compte acct-7 est introuvable"));
  }

  @Test
  void exposesFailureExtensions() throws Exception {
    this.mockMvc
        .perform(get("/validation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email").value("must be valid"));
  }

  @Test
  void rejectsErrValuesThatDoNotImplementFailure() {
    final ServletException exception =
        assertThrows(ServletException.class, () -> this.mockMvc.perform(get("/non-failure")));
    final IllegalStateException cause =
        assertInstanceOf(IllegalStateException.class, exception.getCause());
    assertTrue(cause.getMessage().contains("must implement Failure"));
  }

  @Test
  void leavesOrdinaryExceptionsToNormalSpringHandling() {
    final ServletException exception =
        assertThrows(
            ServletException.class, () -> this.mockMvc.perform(get("/ordinary-exception")));
    final IllegalArgumentException cause =
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    assertEquals("ordinary failure", cause.getMessage());
  }

  private static MethodParameter returnType(String methodName) throws NoSuchMethodException {
    final Method method = TestController.class.getDeclaredMethod(methodName);
    return new MethodParameter(method, -1);
  }
}
