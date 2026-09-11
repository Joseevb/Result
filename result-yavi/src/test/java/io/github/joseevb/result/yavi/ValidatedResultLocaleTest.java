package io.github.joseevb.result.yavi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import am.ik.yavi.builder.ValidatorBuilder;
import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.core.Validatable;
import io.github.joseevb.result.Result;
import java.util.Locale;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class ValidatedResultLocaleTest {

  record User(String name) {}

  private static final Validatable<User> USER_VALIDATOR =
      ValidatorBuilder.<User>of().constraint(User::name, "name", c -> c.notBlank()).build();

  @Test
  void forwardsLocaleToYaviAndPreservesItOnViolations() {
    final Result<User, ConstraintViolations> result =
        ValidatedResult.validate(new User(""), USER_VALIDATOR, Locale.JAPANESE);

    @SuppressWarnings("unchecked")
    final Result.Err<User, ConstraintViolations> err = assertInstanceOf(Result.Err.class, result);
    assertEquals(Locale.JAPANESE, err.error().getFirst().locale());
  }

  @Test
  void returnsOriginalTargetWhenLocalizedValidationSucceeds() {
    final User user = new User("Jose");

    final Result<User, ConstraintViolations> result =
        ValidatedResult.validate(user, USER_VALIDATOR, Locale.GERMAN);

    assertEquals(user, assertInstanceOf(Result.Ok.class, result).value());
  }

  @Test
  @SuppressWarnings("all")
  void rejectsNullLocale() {
    assertThrows(
        NullPointerException.class,
        () -> ValidatedResult.validate(new User("Jose"), USER_VALIDATOR, (Locale) null));
  }
}
