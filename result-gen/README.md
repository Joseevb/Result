# result-gen prototype

Experimental build-time source transformer for [Result issue #2](https://github.com/Joseevb/Result/issues/2).

The transformer extends the existing `Result.gen` API from the parent PR. Each bound Result's
error is mapped into a generated sealed error union before it is passed to `GenScope.bind`.
That keeps fail-fast control flow in `Result.gen` itself while giving unrelated error types one
closed, exhaustive type for pattern matching.

## Current scope

- One `Result.gen($ -> { ... })` call per source file.
- Bind statements must be simple `var name = $.bind(resultExpression);` declarations.
- Each bind argument must resolve to a concrete `io.github.joseevb.result.Result<T, E>`.
- Generic method/type-variable error types, wildcard captures, multiple declarators,
  and nested generator calls are not supported yet.
- Source-level error switches must use arrow-style type patterns and cover every bound error type.
- Generated source is compiled normally by javac; no compiler internals are patched.
- Run the transformer before javac compiles the original source.

## Example input

```java
var result = Result.gen($ -> {
  var user = $.bind(getUser(id));
  var products = $.bind(getProducts(user.id()));
  return new Cart(user, products);
});
```

The transformer changes each bind to map its error into a generated union variant. It rewrites
switch labels such as `case UserError error ->` into record patterns over that union, and reports
missing alternatives before javac runs.

This is deliberately a source transformer, not an annotation processor. The Java source-level
syntax requires the build transform; an unmodified javac invocation cannot compile an exhaustive
switch over a union that Java itself cannot express.
