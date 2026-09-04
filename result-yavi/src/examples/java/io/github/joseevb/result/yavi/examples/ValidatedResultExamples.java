package io.github.joseevb.result.yavi.examples;

import am.ik.yavi.arguments.Arguments3Validator;
import am.ik.yavi.builder.ValidatorBuilder;
import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.core.Validatable;
import am.ik.yavi.validator.Yavi;
import io.github.joseevb.result.Result;
import io.github.joseevb.result.yavi.ValidatedResult;

public final class ValidatedResultExamples {
  private ValidatedResultExamples() {}

  record User(String name, String email, int age) {}

  record UserError(ConstraintViolations violations) {}

  private static final Arguments3Validator<String, String, Integer, User> USER_ARGUMENTS =
      Yavi.arguments()
          ._string("name", c -> c.notBlank())
          ._string("email", c -> c.notBlank().email())
          ._integer("age", c -> c.greaterThanOrEqual(18))
          .apply(User::new);

  private static final Validatable<User> USER_VALIDATOR =
      ValidatorBuilder.<User>of()
          .constraint(User::name, "name", c -> c.notBlank())
          .constraint(User::email, "email", c -> c.notBlank().email())
          .constraint(User::age, "age", c -> c.greaterThanOrEqual(18))
          .build();

  public static void main(String... _) {
    final Result<User, ConstraintViolations> constructed =
        ValidatedResult.from(USER_ARGUMENTS.validate("Jose", "jose@example.com", 21));

    final User user = new User("Jose", "jose@example.com", 21);
    final Result<String, UserError> persisted =
        ValidatedResult.validate(user, USER_VALIDATOR)
            .mapErr(UserError::new)
            .andThen(ValidatedResultExamples::persist);

    IO.println("Argument validation: " + constructed);
    IO.println("Object validation: " + persisted);
  }

  private static Result<String, UserError> persist(User user) {
    return Result.ok(user.email());
  }
}
