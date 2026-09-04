package io.github.joseevb.result.yavi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import am.ik.yavi.arguments.Arguments3Validator;
import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.validator.Yavi;
import io.github.joseevb.result.Result;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class ValidatedResultIntegrationTest {

  record User(String name, String email, int age) {}

  private static final Arguments3Validator<String, String, Integer, User> USER_ARGUMENTS =
      Yavi.arguments()
          ._string("name", c -> c.notBlank())
          ._string("email", c -> c.notBlank().email())
          ._integer("age", c -> c.greaterThanOrEqual(18))
          .apply(User::new);

  @Test
  void convertsSuccessfulArgumentValidation() {
    final Result<User, ConstraintViolations> result =
        ValidatedResult.from(USER_ARGUMENTS.validate("Jose", "jose@example.com", 21));

    final Result.Ok<User, ConstraintViolations> ok = assertInstanceOf(Result.Ok.class, result);
    assertEquals(new User("Jose", "jose@example.com", 21), ok.value());
  }

  @Test
  void preservesAllArgumentValidationFailures() {
    final Result<User, ConstraintViolations> result =
        ValidatedResult.from(USER_ARGUMENTS.validate("", "invalid", 15));

    final Result.Err<User, ConstraintViolations> err =
        assertInstanceOf(Result.Err.class, result);
    assertEquals(3, err.error().size());
    assertEquals("name", err.error().get(0).name());
    assertEquals("email", err.error().get(1).name());
    assertEquals("age", err.error().get(2).name());
  }

  @Test
  void mapsViolationsAndChainsSuccessfulValidation() {
    final Result<String, String> success =
        ValidatedResult.from(USER_ARGUMENTS.validate("Jose", "jose@example.com", 21))
            .<String>mapErr(violations -> "violations=" + violations.size())
            .andThen(user -> Result.ok(user.email()));

    final Result<String, String> failure =
        ValidatedResult.from(USER_ARGUMENTS.validate("", "invalid", 15))
            .mapErr(violations -> "violations=" + violations.size())
            .andThen(user -> Result.ok(user.email()));

    assertEquals("jose@example.com", assertInstanceOf(Result.Ok.class, success).value());
    assertEquals("violations=3", assertInstanceOf(Result.Err.class, failure).error());
  }
}
