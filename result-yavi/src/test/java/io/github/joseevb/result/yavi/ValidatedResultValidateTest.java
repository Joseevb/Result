package io.github.joseevb.result.yavi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import am.ik.yavi.builder.ValidatorBuilder;
import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.core.Validatable;
import io.github.joseevb.result.Result;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class ValidatedResultValidateTest {

  record User(String name, String email, int age) {}

  private static final Validatable<User> USER_VALIDATOR =
      ValidatorBuilder.<User>of()
          .constraint(User::name, "name", c -> c.notBlank())
          .constraint(User::email, "email", c -> c.notBlank().email())
          .constraint(User::age, "age", c -> c.greaterThanOrEqual(18))
          .build();

  @Test
  void returnsOriginalTargetOnSuccess() {
    final User user = new User("Jose", "jose@example.com", 21);

    final Result<User, ConstraintViolations> result =
        ValidatedResult.validate(user, USER_VALIDATOR);

    final Result.Ok<User, ConstraintViolations> ok = assertInstanceOf(Result.Ok.class, result);
    assertSame(user, ok.value());
  }

  @Test
  void preservesTheValidatorViolationsObject() {
    final User user = new User("", "invalid", 15);
    final ConstraintViolations violations = USER_VALIDATOR.validate(user);
    final Validatable<Object> validator = (_, _, _) -> violations;

    final Result<User, ConstraintViolations> result = ValidatedResult.validate(user, validator);

    final Result.Err<User, ConstraintViolations> err =
        assertInstanceOf(Result.Err.class, result);
    assertSame(violations, err.error());
    assertEquals(3, err.error().size());
  }

  @Test
  @SuppressWarnings("all")
  void leavesNullTargetHandlingToYavi() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ValidatedResult.validate(null, USER_VALIDATOR));
  }

  @Test
  @SuppressWarnings("all")
  void rejectsNullValidator() {
    final User user = new User("Jose", "jose@example.com", 21);

    assertThrows(
        NullPointerException.class,
        () -> ValidatedResult.validate(user, null));
  }
}
