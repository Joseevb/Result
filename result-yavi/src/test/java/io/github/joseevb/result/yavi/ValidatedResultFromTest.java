package io.github.joseevb.result.yavi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import am.ik.yavi.core.ConstraintViolation;
import am.ik.yavi.core.ConstraintViolations;
import am.ik.yavi.core.Validated;
import io.github.joseevb.result.Result;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class ValidatedResultFromTest {

  @Test
  void convertsSuccessfulValidatedWithCovariance() {
    final String value = "validated";

    final Result<CharSequence, ConstraintViolations> result =
        ValidatedResult.from(Validated.successWith(value));

    final Result.Ok<CharSequence, ConstraintViolations> ok =
        assertInstanceOf(Result.Ok.class, result);
    assertSame(value, ok.value());
  }

  @Test
  void preservesMultipleViolations() {
    final ConstraintViolation nameViolation =
        ConstraintViolation.builder().name("name").message("name is required");
    final ConstraintViolation emailViolation =
        ConstraintViolation.builder().name("email").message("email is invalid");

    final Result<Object, ConstraintViolations> result =
        ValidatedResult.from(Validated.failureWith(nameViolation, emailViolation));

    final Result.Err<Object, ConstraintViolations> err =
        assertInstanceOf(Result.Err.class, result);
    assertEquals(2, err.error().size());
    assertSame(nameViolation, err.error().get(0));
    assertSame(emailViolation, err.error().get(1));
  }

  @Test
  void rejectsSuccessfulNullValue() {
    assertThrows(
        NullPointerException.class,
        () -> ValidatedResult.from(Validated.<Object>successWith(null)));
  }

  @Test
  @SuppressWarnings("all")
  void rejectsNullValidated() {
    assertThrows(NullPointerException.class, () -> ValidatedResult.from(null));
  }
}
