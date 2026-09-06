# Generator-style Result composition

Java 25 cannot represent the ideal error type of a heterogeneous generator:

```java
Result<Cart, UserError | ProductError>
```

`Result.gen` consequently provides two type-safe forms with different inference ergonomics.

## Direct form

```java
var result = Result.gen($ -> {
    var user = $.bind(getUser(id));
    var products = $.bind(getProducts(user.id()));
    return new Cart(user, products);
});
```

With `var`, Java 25 infers `Result<Cart, Object>`. It does so even if `UserError` and
`ProductError` have a shared `CartError` parent. The outer invocation must target-type its lambda
before type-checking the body, and the calls to `$.bind(...)` do not contribute bounds back to the
outer `E` inference variable.

Assignment context can provide `E` instead:

```java
Result<Cart, CartError> result = Result.gen($ -> {
    var user = $.bind(getUser(id));
    var products = $.bind(getProducts(user.id()));
    return new Cart(user, products);
});
```

The compiler then rejects any bound Result whose error does not extend `CartError`.

The complete method type arguments can be supplied when `var` is preferred without a staged call:

```java
var result = Result.<Cart, CartError>gen($ -> {
    var user = $.bind(getUser(id));
    var products = $.bind(getProducts(user.id()));
    return new Cart(user, products);
});
```

This keeps the exact `Result<Cart, CartError>` type, at the cost of spelling both the success and
error types.

## Staged form

The staged overload fixes only the error type before the lambda and therefore lets `var` infer the
success type:

```java
var result = Result.<CartError>gen().run($ -> {
    var user = $.bind(getUser(id));
    var products = $.bind(getProducts(user.id()));
    return new Cart(user, products);
});
```

Here the exact type is `Result<Cart, CartError>`. This is the most precise option when the
application already has an appropriate error hierarchy.

## Why the API stops here

- General union types are not part of Java's type system. Intersection types describe values that
  satisfy every bound, which is different from accepting one of several unrelated errors.
- A generator scope has one static type for the entire lambda. A `bind` call cannot mutate that type
  so that a later statement sees a new accumulated error set.
- Encoding a growing sum with `Union2`, `Union3`, and so on imposes an arity limit and changes the
  error value returned to the caller.
- Accepting `Result<T, ?>` and casting its error to a caller-selected `E` would allow an unrelated
  runtime value inside `Result<T, E>`. This implementation does not perform that cast.

The chosen API scales to any number of dependent binds, preserves each original error object, and
either reports `Object` honestly or requires the compiler to prove that every error is a subtype of
the selected `E`.

## Runtime behavior

`bind` returns the value of an `Ok`. For an `Err`, it records the error and throws a private,
stackless control-flow Error which `Result.gen` catches. Extending `Error` keeps ordinary
`catch (Exception)` blocks and `Result.from` from intercepting the signal. Each scope has its own
signal, allowing nested generators to propagate the correct outer failure. Application exceptions
and errors are not converted to domain errors.

The scope is valid only for the dynamic extent of its generator. Because Java cannot provide an
uncatchable control-flow signal, application code must not catch `Error` or `Throwable` around
`bind`. If it does catch the internal signal, `gen` still returns the recorded `Err`, but code inside
that catch boundary may already have run and strict short-circuiting cannot be guaranteed.
