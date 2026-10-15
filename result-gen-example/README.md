# Result.gen source-transformer example

This project exercises the source transformer as a build step rather than testing only the
transformer API.

The input is `src/main/java/demo/Main.java`. The `transformResultGen` task runs the transformer,
writes rewritten source under `build/generated/sources/resultGen/main`, and then javac compiles it.

From the repository root, with JDK 25 installed:

```sh
./gradlew :result-gen-example:runExample
```

The demo uses unrelated `UserError` and `ProductError` records. The source uses the existing
`Result.gen` API and matches the original error types:

```java
return switch (err.error()) {
  case UserError error -> error.message();
  case ProductError error -> error.message();
};
```

The transformer maps each bound error into a generated sealed-union variant and rewrites the
switch labels to record patterns, preserving each payload's original static type. It rejects a
switch that omits an alternative, and javac also checks exhaustiveness against the generated union.

To inspect generated source, open
`result-gen-example/build/generated/sources/resultGen/main/demo/Main.java`.

This remains an experimental prototype. It supports one `Result.gen` invocation per compilation
unit, simple `var x = $.bind(...)` declarations, distinct error types, and arrow-style type-pattern
cases. The original source syntax is accepted by the build transformer, not by unmodified javac.
IDE completion and diagnostics in the untransformed source still need editor/build-tool integration.
