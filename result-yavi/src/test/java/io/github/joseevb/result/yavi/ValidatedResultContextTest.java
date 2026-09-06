package io.github.joseevb.result.yavi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import am.ik.yavi.core.ConstraintContext;
import am.ik.yavi.core.ConstraintViolation;
import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.core.Validatable;
import io.github.joseevb.result.Result;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class ValidatedResultContextTest {

  record User(String name) {}

  private static final Validatable<User> CONTEXTUAL_VALIDATOR =
      (target, locale, context) -> {
        final ConstraintViolations violations = new ConstraintViolations();
        if (context.attribute("strict").isEqualTo(true) && target.name().isBlank()) {
          violations.add(
              ConstraintViolation.builder()
                  .name("name")
                  .messageKey("name.required")
                  .defaultMessageFormat("name is required")
                  .locale(locale)
                  .build());
        }
        return violations;
      };

  @Test
  void respectsConstraintContextAttributes() {
    final User user = new User("");
    final ConstraintContext relaxed = ConstraintContext.from(Map.of("strict", false));
    final ConstraintContext strict = ConstraintContext.from(Map.of("strict", true));

    final Result<User, ConstraintViolations> relaxedResult =
        ValidatedResult.validate(user, CONTEXTUAL_VALIDATOR, relaxed);
    final Result<User, ConstraintViolations> strictResult =
        ValidatedResult.validate(user, CONTEXTUAL_VALIDATOR, strict);

    assertInstanceOf(Result.Ok.class, relaxedResult);
    final Result.Err<User, ConstraintViolations> err =
        assertInstanceOf(Result.Err.class, strictResult);
    assertEquals("name", err.error().getFirst().name());
  }

  @Test
  void forwardsLocaleAndContextTogether() {
    final ConstraintContext strict = ConstraintContext.from(Map.of("strict", true));

    final Result<User, ConstraintViolations> result =
        ValidatedResult.validate(new User(""), CONTEXTUAL_VALIDATOR, Locale.FRENCH, strict);

    final Result.Err<User, ConstraintViolations> err = assertInstanceOf(Result.Err.class, result);
    assertEquals(Locale.FRENCH, err.error().getFirst().locale());
  }

  @Test
  @SuppressWarnings("all")
  void rejectsNullContext() {
    assertThrows(
        NullPointerException.class,
        () ->
            ValidatedResult.validate(
                new User("Jose"), CONTEXTUAL_VALIDATOR, (ConstraintContext) null));
  }
}
