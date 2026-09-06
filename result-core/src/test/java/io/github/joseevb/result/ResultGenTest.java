package io.github.joseevb.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultGenTest {

  record User(String id) {}

  record Product(String name) {}

  record Cart(User user, List<Product> products) {}

  sealed interface CartError permits UserError, ProductError {}

  record UserError(String id) implements CartError {}

  record ProductError(String userId) implements CartError {}

  record UnrelatedUserError(String id) {}

  record UnrelatedProductError(String userId) {}

  private static Result<User, UserError> user(String id) {
    return Result.ok(new User(id));
  }

  private static Result<List<Product>, ProductError> products(String userId) {
    return Result.ok(List.of(new Product("product-for-" + userId)));
  }

  private static Result<User, UnrelatedUserError> unrelatedUser(String id) {
    return Result.ok(new User(id));
  }

  private static Result<List<Product>, UnrelatedProductError> unrelatedProducts(String userId) {
    return Result.ok(List.of(new Product("product-for-" + userId)));
  }

  @Nested
  @DisplayName("Result.gen(body)")
  class DirectGenTests {

    @Test
    @DisplayName("binds successful Results in declaration order")
    void bindsSuccessfulResults() {
      final var result =
          Result.gen(
              $ -> {
                final var user = $.bind(unrelatedUser("42"));
                final var products = $.bind(unrelatedProducts(user.id()));
                return new Cart(user, products);
              });

      // javac 25 infers Object for the error type of a targetless generator invocation.
      final Result<Cart, Object> inferredAsObject = result;
      assertEquals("42", inferredAsObject.unwrap().user().id());
      assertEquals("product-for-42", inferredAsObject.unwrap().products().getFirst().name());
    }

    @Test
    @DisplayName("an explicit target type retains and validates a shared error type")
    void explicitTargetTypeRetainsSharedError() {
      final Result<Cart, CartError> result =
          Result.gen(
              $ -> {
                final var user = $.bind(user("42"));
                final var products = $.bind(products(user.id()));
                return new Cart(user, products);
              });

      assertTrue(result.isOk());
    }

    @Test
    @DisplayName("returns the first error without evaluating later statements")
    void returnsFirstErrorAndShortCircuits() {
      final var expected = new UnrelatedUserError("missing");
      final var laterStatementRan = new AtomicBoolean(false);

      final var result =
          Result.gen(
              $ -> {
                final var user = $.bind(Result.<User, UnrelatedUserError>err(expected));
                laterStatementRan.set(true);
                final var products = $.bind(unrelatedProducts(user.id()));
                return new Cart(user, products);
              });

      assertFalse(laterStatementRan.get());
      assertSame(expected, assertInstanceOf(Result.Err.class, result).error());
    }

    @Test
    @DisplayName("returns an error from a later bind")
    void returnsLaterError() {
      final var expected = new UnrelatedProductError("42");

      final var result =
          Result.gen(
              $ -> {
                final var user = $.bind(unrelatedUser("42"));
                final var products =
                    $.bind(Result.<List<Product>, UnrelatedProductError>err(expected));
                return new Cart(user, products);
              });

      assertSame(expected, assertInstanceOf(Result.Err.class, result).error());
    }

    @Test
    @DisplayName("rejects null bodies, bound Results, and success values")
    void rejectsNulls() {
      assertThrows(NullPointerException.class, () -> Result.gen(null));
      assertThrows(
          NullPointerException.class,
          () -> Result.<String, Object>gen($ -> $.<String, Object>bind(null)));
      assertThrows(NullPointerException.class, () -> Result.<String, Object>gen(_ -> null));
    }

    @Test
    @DisplayName("does not capture exceptions or errors thrown by user code")
    void doesNotCaptureUserThrowables() {
      final var exception = new IllegalStateException("unexpected");
      final var error = new AssertionError("fatal");

      assertSame(
          exception,
          assertThrows(
              IllegalStateException.class,
              () ->
                  Result.gen(
                      _ -> {
                        throw exception;
                      })));
      assertSame(
          error,
          assertThrows(
              AssertionError.class,
              () ->
                  Result.gen(
                      _ -> {
                        throw error;
                      })));
    }

    @Test
    @DisplayName("a broad catch cannot turn a bound error into an Ok")
    void caughtControlSignalStillProducesError() {
      final var expected = new UnrelatedUserError("missing");

      final Result<String, Object> result =
          Result.gen(
              $ -> {
                try {
                  $.bind(Result.<String, UnrelatedUserError>err(expected));
                } catch (final Error _) {
                  // Deliberately hostile code: Result.gen still remembers the failure.
                }
                try {
                  $.bind(Result.<Integer, UnrelatedProductError>ok(42));
                } catch (final Error _) {
                  // A scope that has failed cannot bind again.
                }
                return "ignored";
              });

      assertSame(expected, assertInstanceOf(Result.Err.class, result).error());
    }

    @Test
    @DisplayName("nested generators do not consume another scope's control signal")
    void nestedGeneratorsKeepControlSignalsScoped() {
      final var expected = new UnrelatedUserError("missing");

      final Result<Result<String, Object>, Object> result =
          Result.gen(
              outer ->
                  Result.<String, Object>gen(
                      _ -> outer.bind(Result.<String, UnrelatedUserError>err(expected))));

      assertSame(expected, assertInstanceOf(Result.Err.class, result).error());
    }
  }

  @Nested
  @DisplayName("Result.gen().run(body)")
  class StagedGenTests {

    @Test
    @DisplayName("keeps the explicit error type when the result uses var")
    void keepsExplicitErrorTypeWithVar() {
      final var result =
          Result.<CartError>gen()
              .run(
                  $ -> {
                    final var user = $.bind(user("42"));
                    final var products = $.bind(products(user.id()));
                    return new Cart(user, products);
                  });

      final Result<Cart, CartError> inferredAsCartError = result;
      assertEquals("42", inferredAsCartError.unwrap().user().id());
    }

    @Test
    @DisplayName("returns a bound subtype as the shared error type")
    void returnsBoundErrorAsSharedType() {
      final var expected = new ProductError("42");

      final var result =
          Result.<CartError>gen()
              .run(
                  $ -> {
                    final var user = $.bind(user("42"));
                    final var products = $.bind(Result.<List<Product>, ProductError>err(expected));
                    return new Cart(user, products);
                  });

      final Result<Cart, CartError> inferredAsCartError = result;
      assertSame(expected, assertInstanceOf(Result.Err.class, inferredAsCartError).error());
    }

    @Test
    @DisplayName("rejects a null body")
    void rejectsNullBody() {
      assertThrows(NullPointerException.class, () -> Result.<CartError>gen().run(null));
    }
  }
}
