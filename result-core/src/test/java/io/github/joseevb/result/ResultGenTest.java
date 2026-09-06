package io.github.joseevb.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultGenTest {

  record User(String id) {}

  record Cart(User user, List<String> products) {}

  sealed interface UserError permits MissingUser {}

  record MissingUser(String id) implements UserError {}

  sealed interface ProductError permits MissingProducts {}

  record MissingProducts(String userId) implements ProductError {}

  sealed interface CartError permits CartUserError, CartProductError {}

  non-sealed interface CartUserError extends CartError {}

  non-sealed interface CartProductError extends CartError {}

  record CartMissingUser(String id) implements CartUserError {}

  record CartMissingProducts(String userId) implements CartProductError {}

  @Nested
  @DisplayName("Result.gen(generator)")
  class InferredGeneratorTests {

    @Test
    @DisplayName("returns the value after binding dependent Ok results")
    void gen_allOk() {
      final var result =
          Result.gen(
              $ -> {
                final var user = $.bind(findUser("42"));
                final var products = $.bind(findProducts(user.id()));
                return new Cart(user, products);
              });

      assertTrue(result.isOk());
      assertEquals(new Cart(new User("42"), List.of("keyboard", "mouse")), result.unwrap());
    }

    @Test
    @DisplayName("infers Object as the safe upper bound of unrelated error interfaces")
    void gen_infersObjectForUnrelatedErrors() {
      final var result =
          Result.gen(
              $ -> {
                final var user = $.bind(findUser("42"));
                final var products = $.bind(findProducts(user.id()));
                return new Cart(user, products);
              });

      final Result<Cart, Object> inferredType = result;
      assertTrue(inferredType.isOk());
    }

    @Test
    @DisplayName("var still infers Object when errors share a nominal supertype")
    void gen_varDoesNotInferSharedErrorSupertype() {
      final var result =
          Result.gen(
              $ -> {
                final var user = $.bind(findCartUser("42"));
                final var products = $.bind(findCartProducts(user.id()));
                return new Cart(user, products);
              });

      final Result<Cart, Object> inferredType = result;
      assertTrue(inferredType.isOk());
    }

    @Test
    @DisplayName("uses a shared error supertype supplied by the assignment context")
    void gen_usesTargetErrorType() {
      final Result<Cart, CartError> result =
          Result.gen(
              $ -> {
                final var user = $.bind(findCartUser("42"));
                final var products = $.bind(findCartProducts(user.id()));
                return new Cart(user, products);
              });

      assertTrue(result.isOk());
    }

    @Test
    @DisplayName("returns the first error and does not evaluate later statements")
    void gen_firstErrShortCircuits() {
      final var missing = new MissingUser("42");
      final var laterCall = new AtomicBoolean(false);

      final Result<Cart, Object> result =
          Result.gen(
              $ -> {
                final var user = $.bind(Result.<User, UserError>err(missing));
                laterCall.set(true);
                final var products = $.bind(findProducts(user.id()));
                return new Cart(user, products);
              });

      assertTrue(result.isErr());
      assertFalse(laterCall.get());
      assertSame(missing, ((Result.Err<Cart, Object>) result).error());
    }

    @Test
    @DisplayName("does not let an accidentally caught bind failure become Ok")
    void gen_caughtAbortStillReturnsErr() {
      final var missing = new MissingUser("42");

      final Result<Integer, UserError> result =
          Result.gen(
              $ -> {
                try {
                  $.bind(Result.<User, UserError>err(missing));
                } catch (RuntimeException _) {
                  // Application code should not catch around bind, but the error must not
                  // disappear.
                }
                return 1;
              });

      assertTrue(result.isErr());
      assertSame(missing, ((Result.Err<Integer, UserError>) result).error());
    }

    @Test
    @DisplayName("propagates application exceptions unchanged")
    void gen_propagatesApplicationException() {
      final var problem = new IllegalArgumentException("boom");

      final var thrown =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  Result.gen(
                      $ -> {
                        throw problem;
                      }));

      assertSame(problem, thrown);
    }

    @Test
    @DisplayName("rejects null generators, bound Results, and generated values")
    void gen_rejectsNulls() {
      assertThrows(NullPointerException.class, () -> Result.gen(null));
      assertThrows(NullPointerException.class, () -> Result.gen($ -> $.bind(null)));
      assertThrows(NullPointerException.class, () -> Result.gen(_ -> null));
    }
  }

  @Nested
  @DisplayName("Result.<E>gen().run(generator)")
  class ExplicitGeneratorTests {

    @Test
    @DisplayName("keeps the selected shared error type when assigned with var")
    void gen_keepsExplicitErrorType() {
      final var result =
          Result.<CartError>gen()
              .run(
                  $ -> {
                    final var user = $.bind(findCartUser("42"));
                    final var products = $.bind(findCartProducts(user.id()));
                    return new Cart(user, products);
                  });

      final Result<Cart, CartError> explicitType = result;
      assertTrue(explicitType.isOk());
    }

    @Test
    @DisplayName("returns the first subtype error as the selected shared type")
    void gen_returnsSubtypeError() {
      final var missing = new CartMissingProducts("42");

      final Result<Cart, CartError> result =
          Result.<CartError>gen()
              .run(
                  $ -> {
                    final var user = $.bind(findCartUser("42"));
                    final var products =
                        $.bind(Result.<List<String>, CartProductError>err(missing));
                    return new Cart(user, products);
                  });

      assertTrue(result.isErr());
      assertSame(missing, ((Result.Err<Cart, CartError>) result).error());
    }

    @Test
    @DisplayName("rejects a null generator")
    void gen_rejectsNullGenerator() {
      assertThrows(NullPointerException.class, () -> Result.<CartError>gen().run(null));
    }
  }

  @Nested
  @DisplayName("generator scope lifetime")
  class GeneratorScopeTests {

    @Test
    @DisplayName("cannot be used after its generator finishes")
    void scope_cannotEscape() {
      final var escaped = new AtomicReference<Result.GenScope<UserError>>();

      final Result<Integer, UserError> result =
          Result.gen(
              $ -> {
                escaped.set($);
                return 1;
              });

      assertTrue(result.isOk());
      assertThrows(
          IllegalStateException.class, () -> escaped.get().bind(Result.<Integer, UserError>ok(2)));
    }

    @Test
    @DisplayName("an outer bind failure passes through a nested generator")
    void scope_outerFailurePassesThroughNestedGenerator() {
      final var missing = new MissingUser("42");

      final Result<Integer, UserError> result =
          Result.gen(
              outer -> {
                Result.<UserError>gen()
                    .run(
                        _ -> {
                          outer.bind(Result.<User, UserError>err(missing));
                          return 1;
                        });
                return 2;
              });

      assertTrue(result.isErr());
      assertSame(missing, ((Result.Err<Integer, UserError>) result).error());
    }
  }

  private static Result<User, UserError> findUser(String id) {
    return Result.ok(new User(id));
  }

  private static Result<List<String>, ProductError> findProducts(String userId) {
    return Result.ok(List.of("keyboard", "mouse"));
  }

  private static Result<User, CartUserError> findCartUser(String id) {
    return Result.ok(new User(id));
  }

  private static Result<List<String>, CartProductError> findCartProducts(String userId) {
    return Result.ok(List.of("keyboard", "mouse"));
  }
}
