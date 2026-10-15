package demo;

import io.github.joseevb.result.Result;
import java.util.List;

public class Main {
  record User(long id, String name) {}

  record Product(long id, String name) {}

  record Cart(User user, List<Product> products) {}

  // Deliberately independent error records: neither extends a shared application error type.
  record UserError(long id, String message) {}

  record ProductError(long userId, String message) {}

  static Result<User, UserError> getUser(long id) {
    return id == 0
        ? Result.err(new UserError(id, "user not found"))
        : Result.ok(new User(id, "Ada"));
  }

  static Result<List<Product>, ProductError> getProducts(long userId) {
    return userId == 13
        ? Result.err(new ProductError(userId, "catalog unavailable"))
        : Result.ok(List.of(new Product(7, "Keyboard"), new Product(8, "Mouse")));
  }

  static Result<Cart, ?> getCart(long id) {
    return Result.gen(
        $ -> {
          var user = $.bind(getUser(id));
          var products = $.bind(getProducts(user.id()));
          return new Cart(user, products);
        });
  }

  public static void main(String[] args) {
    var success = getCart(42);
    if (!success.isOk() || !success.unwrap().products().getFirst().name().equals("Keyboard")) {
      throw new AssertionError("Expected successful cart, got " + success);
    }

    assertErrorMessage(getCart(0), "user not found");
    assertErrorMessage(getCart(13), "catalog unavailable");

    System.out.println("PASS: success returns Cart");
    System.out.println("PASS: switch matches UserError directly");
    System.out.println("PASS: switch matches ProductError directly");
    System.out.println("PASS: error switch is exhaustive over the generated union");
  }

  private static void assertErrorMessage(Result<Cart, ?> result, String expectedMessage) {
    if (!(result instanceof Result.Err<Cart, ?> err)) {
      throw new AssertionError("Expected Err, got " + result);
    }

    String message =
        switch (err.error()) {
          case UserError error -> error.message();
          case ProductError error -> error.message();
        };
    if (!message.equals(expectedMessage)) {
      throw new AssertionError("Expected message " + expectedMessage + ", got " + message);
    }
  }
}
