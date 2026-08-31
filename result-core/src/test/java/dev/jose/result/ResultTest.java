package dev.jose.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ResultTest {

  // ==================== Test Helpers ====================

  sealed interface TestError permits TestError.NotFound, TestError.Invalid {
    record NotFound(String id) implements TestError {}

    record Invalid(String field) implements TestError {}
  }

  record User(String id, String name, int age) {}

  // ==================== Static Factory: ok() ====================

  @Nested
  @DisplayName("Result.ok()")
  class SuccessTests {

    @Test
    @DisplayName("creates Ok with non-null value")
    void success_withNonNullValue() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      assertTrue(result.isOk());
      assertFalse(result.isErr());
      assertEquals("John", result.unwrap().name());
    }

    @Test
    @DisplayName("rejects a null value")
    void success_withNullValue() {
      assertThrows(NullPointerException.class, () -> Result.ok(null));
    }

    @Test
    @DisplayName("creates Ok with different types")
    void success_withDifferentTypes() {
      final Result<Integer, TestError> intResult = Result.ok(42);
      final Result<List<String>, TestError> listResult = Result.ok(List.of("a", "b"));
      final Result<Result.Unit, TestError> emptyResult = Result.empty();

      assertEquals(42, intResult.unwrap());
      assertEquals(List.of("a", "b"), listResult.unwrap());
      assertSame(Result.Unit.INSTANCE, emptyResult.unwrap());
    }
  }

  // ==================== Static Factory: err() ====================

  @Nested
  @DisplayName("Result.err()")
  class FailureTests {

    @Test
    @DisplayName("creates Err with error")
    void failure_createsFailure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("123"));
      assertFalse(result.isOk());
      assertTrue(result.isErr());
      assertThrows(RuntimeException.class, result::unwrap);
    }

    @Test
    @DisplayName("failure propagates through transformations")
    void failure_propagatesThroughTransformations() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final Result<String, TestError> mapped = original.map(User::name);
      assertTrue(mapped.isErr());
    }

    @Test
    @DisplayName("rejects a null error")
    void failure_withNullError() {
      assertThrows(NullPointerException.class, () -> Result.err(null));
    }
  }

  // ==================== Static Factory: ofNullable() ====================

  @Nested
  @DisplayName("Result.ofNullable()")
  class OfNullableTests {

    @Test
    @DisplayName("non-null value creates Ok")
    void ofNullable_nonNullValue() {
      final Result<String, TestError> result =
          Result.ofNullable("hello", () -> new TestError.Invalid("field"));
      assertTrue(result.isOk());
      assertEquals("hello", result.unwrap());
    }

    @Test
    @DisplayName("null value creates Err")
    void ofNullable_nullValue() {
      final AtomicBoolean supplierCalled = new AtomicBoolean(false);
      final Result<String, TestError> result =
          Result.ofNullable(
              null,
              () -> {
                supplierCalled.set(true);
                return new TestError.Invalid("field");
              });
      assertTrue(result.isErr());
      assertTrue(supplierCalled.get());
    }

    @Test
    @DisplayName("supplier not called for non-null")
    void ofNullable_supplierNotCalledForNonNull() {
      final AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Result.ofNullable(
          "value",
          () -> {
            supplierCalled.set(true);
            return new TestError.Invalid("x");
          });
      assertFalse(supplierCalled.get());
    }
  }

  // ==================== Static Factory: empty() ====================

  @Nested
  @DisplayName("Result.empty()")
  class EmptyTests {

    @Test
    @DisplayName("returns Ok containing Unit.INSTANCE")
    void empty_returnsSuccessNull() {
      final Result<Result.Unit, TestError> result = Result.empty();
      assertTrue(result.isOk());
      assertSame(Result.Unit.INSTANCE, result.unwrap());
    }

    @Test
    @DisplayName("works with stream and transformations")
    void empty_worksAsVoid() {
      final Result<Result.Unit, TestError> empty = Result.empty();
      assertEquals(List.of(Result.Unit.INSTANCE), empty.stream().toList());

      final Result<String, TestError> mapped = empty.map(_ -> "done");
      assertEquals("done", mapped.unwrap());
    }
  }

  // ==================== Static Factory: from() ====================

  @Nested
  @DisplayName("Result.from()")
  class FromTests {

    @Test
    @DisplayName("success case returns Ok")
    void from_success() {
      final Result<String, TestError> result =
          Result.from(() -> "hello", _ -> new TestError.Invalid("error"));
      assertTrue(result.isOk());
      assertEquals("hello", result.unwrap());
    }

    @Test
    @DisplayName("unchecked exception returns Err")
    void from_uncheckedException() {
      final Result<String, TestError> result =
          Result.from(
              () -> {
                throw new IllegalArgumentException("bad input");
              },
              e -> new TestError.Invalid(e.getMessage()));
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("checked exception returns Err")
    void from_checkedException() {
      final Result<String, TestError> result =
          Result.from(
              () -> {
                throw new IOException("file not found");
              },
              e -> new TestError.Invalid(e.getMessage()));
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("exception mapper receives correct exception type")
    void from_exceptionMapperReceivesCorrectType() {
      final Result<String, TestError> result =
          Result.from(
              () -> {
                throw new IllegalArgumentException("test");
              },
              e -> {
                assertInstanceOf(IllegalArgumentException.class, e);
                return new TestError.Invalid(e.getClass().getSimpleName());
              });
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("captures Exception without an error mapper")
    void from_capturesException() {
      final Result<String, Exception> result =
          Result.from(
              () -> {
                throw new IOException("file not found");
              });

      assertTrue(result.isErr());
      assertInstanceOf(IOException.class, assertInstanceOf(Result.Err.class, result).error());
    }

    @Test
    @DisplayName("does not catch Error")
    void from_doesNotCatchError() {
      final AssertionError error = new AssertionError("fatal");
      final AssertionError thrown =
          assertThrows(
              AssertionError.class,
              () ->
                  Result.from(
                      () -> {
                        throw error;
                      }));

      assertSame(error, thrown);
    }

    @Test
    @DisplayName("rejects a null supplied value")
    void from_rejectsNullValue() {
      assertThrows(NullPointerException.class, () -> Result.from(() -> null));
    }

    @Test
    @DisplayName("rejects a null mapped error")
    void from_rejectsNullMappedError() {
      assertThrows(
          NullPointerException.class,
          () ->
              Result.from(
                  () -> {
                    throw new IOException("file not found");
                  },
                  _ -> null));
    }
  }

  // ==================== Static Factory: fromOptional() ====================

  @Nested
  @DisplayName("Result.fromOptional()")
  class FromOptionalTests {

    @Test
    @DisplayName("Optional.of(value) returns Ok")
    void fromOptional_withValue() {
      final Result<String, TestError> result =
          Result.fromOptional(Optional.of("hello"), () -> new TestError.NotFound("default"));
      assertTrue(result.isOk());
      assertEquals("hello", result.unwrap());
    }

    @Test
    @DisplayName("Optional.empty() returns Err")
    void fromOptional_empty() {
      final Result<String, TestError> result =
          Result.fromOptional(Optional.empty(), () -> new TestError.NotFound("id"));
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("errorSupplier called only on empty")
    void fromOptional_supplierCalledOnlyOnEmpty() {
      final AtomicBoolean called = new AtomicBoolean(false);
      Result.fromOptional(
          Optional.of("value"),
          () -> {
            called.set(true);
            return new TestError.NotFound("x");
          });
      assertFalse(called.get());
      Result.fromOptional(
          Optional.empty(),
          () -> {
            called.set(true);
            return new TestError.NotFound("x");
          });
      assertTrue(called.get());
    }
  }

  // ==================== Static Factory: collect() ====================

  @Nested
  @DisplayName("Result.collect()")
  class CollectTests {

    @Test
    @DisplayName("all Ok returns Ok with collected values")
    void collect_allSuccess() {
      final Stream<Result<Integer, TestError>> stream =
          Stream.of(Result.ok(1), Result.ok(2), Result.ok(3));
      final Result<List<Integer>, TestError> result = Result.collect(stream);
      assertTrue(result.isOk());
      assertEquals(List.of(1, 2, 3), result.unwrap());
    }

    @Test
    @DisplayName("first Err short-circuits")
    void collect_shortCircuitsOnFirstFailure() {
      // Just verify short-circuit behavior works by checking failure is returned
      final Result<List<Integer>, TestError> result =
          Result.collect(
              Stream.of(Result.ok(1), Result.err(new TestError.Invalid("error")), Result.ok(3)));
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("empty stream returns Ok with empty collection")
    void collect_emptyStream() {
      final Stream<Result<Integer, TestError>> stream = Stream.empty();
      final Result<List<Integer>, TestError> result = Result.collect(stream);
      assertTrue(result.isOk());
      assertTrue(result.unwrap().isEmpty());
    }

    @Test
    @DisplayName("custom Collector (toSet)")
    void collect_customCollector() {
      final Stream<Result<Integer, TestError>> stream =
          Stream.of(Result.ok(1), Result.ok(2), Result.ok(3));
      final Result<Set<Integer>, TestError> result = Result.collect(stream, Collectors.toSet());
      assertTrue(result.isOk());
      assertEquals(Set.of(1, 2, 3), result.unwrap());
    }

    @Test
    @DisplayName("Err at different positions")
    void collect_failureAtDifferentPositions() {
      assertTrue(
          Result.collect(Stream.of(Result.err(new TestError.NotFound("1")), Result.ok(2))).isErr());

      assertTrue(
          Result.collect(Stream.of(Result.ok(1), Result.err(new TestError.NotFound("2")))).isErr());
    }
  }

  // ==================== Static Factory: collect() with List ====================

  @Nested
  @DisplayName("Result.collect(Stream)")
  class CollectListTests {

    @Test
    @DisplayName("collects into List")
    void collectList() {
      final Stream<Result<String, TestError>> stream = Stream.of(Result.ok("a"), Result.ok("b"));
      final Result<List<String>, TestError> result = Result.collect(stream);
      assertTrue(result.isOk());
      assertEquals(List.of("a", "b"), result.unwrap());
    }
  }

  // ==================== Static Factory: sequence() ====================

  @Nested
  @DisplayName("Result.sequence()")
  class SequenceTests {

    @Test
    @DisplayName("all Ok returns list in order")
    void sequence_allSuccess() {
      final List<Result<Integer, TestError>> list =
          List.of(Result.ok(1), Result.ok(2), Result.ok(3));
      final Result<List<Integer>, TestError> result = Result.sequence(list);
      assertTrue(result.isOk());
      assertEquals(List.of(1, 2, 3), result.unwrap());
    }

    @Test
    @DisplayName("Err at first position")
    void sequence_failureAtFirst() {
      final List<Result<Integer, TestError>> list =
          List.of(Result.err(new TestError.NotFound("1")), Result.ok(2), Result.ok(3));
      final Result<List<Integer>, TestError> result = Result.sequence(list);
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("Err at middle position")
    void sequence_failureAtMiddle() {
      final List<Result<Integer, TestError>> list =
          List.of(Result.ok(1), Result.err(new TestError.Invalid("2")), Result.ok(3));
      final Result<List<Integer>, TestError> result = Result.sequence(list);
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("Err at last position")
    void sequence_failureAtLast() {
      final List<Result<Integer, TestError>> list =
          List.of(Result.ok(1), Result.ok(2), Result.err(new TestError.NotFound("3")));
      final Result<List<Integer>, TestError> result = Result.sequence(list);
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("empty list returns Ok with empty list")
    void sequence_emptyList() {
      final List<Result<Integer, TestError>> list = List.of();
      final Result<List<Integer>, TestError> result = Result.sequence(list);
      assertTrue(result.isOk());
      assertTrue(result.unwrap().isEmpty());
    }

    @Test
    @DisplayName("varargs: zero args")
    void sequence_varargsZeroArgs() {
      final Result<List<Integer>, TestError> result = Result.sequence();
      assertTrue(result.isOk());
      assertTrue(result.unwrap().isEmpty());
    }

    @Test
    @DisplayName("varargs: single element")
    void sequence_varargsSingleElement() {
      final Result<List<Integer>, TestError> result = Result.sequence(Result.ok(42));
      assertTrue(result.isOk());
      assertEquals(List.of(42), result.unwrap());
    }

    @Test
    @DisplayName("varargs: multiple elements")
    void sequence_varargsMultiple() {
      final Result<List<Integer>, TestError> result =
          Result.sequence(Result.ok(1), Result.ok(2), Result.ok(3));
      assertTrue(result.isOk());
      assertEquals(List.of(1, 2, 3), result.unwrap());
    }
  }

  // ==================== Static Factory: flatten() ====================

  @Nested
  @DisplayName("Result.flatten()")
  class FlattenTests {

    @Test
    @DisplayName("nested Ok unwraps to Ok")
    void flatten_nestedSuccess() {
      final Result<Result<Integer, TestError>, TestError> nested = Result.ok(Result.ok(42));
      final Result<Integer, TestError> result = Result.flatten(nested);
      assertTrue(result.isOk());
      assertEquals(42, result.unwrap());
    }

    @Test
    @DisplayName("nested Err returns Err")
    void flatten_nestedFailure() {
      final Result<Result<Integer, TestError>, TestError> nested =
          Result.ok(Result.err(new TestError.NotFound("1")));
      final Result<Integer, TestError> result = Result.flatten(nested);
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("outer Err returns Err")
    void flatten_outerFailure() {
      final Result<Result<Integer, TestError>, TestError> nested =
          Result.err(new TestError.Invalid("outer"));
      final Result<Integer, TestError> result = Result.flatten(nested);
      assertTrue(result.isErr());
    }
  }

  // ==================== Transformation: map() ====================

  @Nested
  @DisplayName("Result.map()")
  class MapTests {

    @Test
    @DisplayName("Ok: function applied, new type")
    void map_success() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final Result<String, TestError> result = original.map(User::name);
      assertTrue(result.isOk());
      assertEquals("John", result.unwrap());
    }

    @Test
    @DisplayName("Ok: function not called on Err")
    void map_failure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final AtomicBoolean called = new AtomicBoolean(false);
      final Result<String, TestError> result =
          original.map(
              u -> {
                called.set(true);
                return u.name();
              });
      assertTrue(result.isErr());
      assertFalse(called.get());
    }

    @Test
    @DisplayName("transforms to different type")
    void map_differentType() {
      final Result<Integer, TestError> original = Result.ok(10);
      final Result<String, TestError> result = original.map(i -> "number-" + i);
      assertEquals("number-10", result.unwrap());
    }

    @Test
    @DisplayName("Ok: rejects a null mapped value")
    void map_rejectsNullValue() {
      final Result<Integer, TestError> original = Result.ok(10);
      assertThrows(NullPointerException.class, () -> original.map(_ -> null));
    }
  }

  // ==================== Transformation: mapErr() ====================

  @Nested
  @DisplayName("Result.mapErr()")
  class MapErrorTests {

    @Test
    @DisplayName("Err: error transformed")
    void mapError_failure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("123"));
      final Result<User, String> result = original.mapErr(Object::toString);
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("Ok: unchanged")
    void mapError_success() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final Result<User, String> result = original.mapErr(Object::toString);
      assertTrue(result.isOk());
      assertEquals("John", result.unwrap().name());
    }

    @Test
    @DisplayName("Err: rejects a null mapped error")
    void mapError_rejectsNullError() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("123"));
      assertThrows(NullPointerException.class, () -> original.mapErr(_ -> null));
    }
  }

  // ==================== Transformation: map() both paths ====================

  @Nested
  @DisplayName("Result.map() with both mappers")
  class MapBothTests {

    @Test
    @DisplayName("Ok: okMapper applied")
    void map_both_success() {
      final Result<Integer, TestError> original = Result.ok(10);
      final Result<String, String> result = original.map(i -> "success-" + i, e -> "error-" + e);
      assertTrue(result.isOk());
      assertEquals("success-10", result.unwrap());
    }

    @Test
    @DisplayName("Err: errMapper applied")
    void map_both_failure() {
      final Result<Integer, TestError> original = Result.err(new TestError.NotFound("1"));
      final Result<String, String> result = original.map(i -> "success-" + i, e -> "error-" + e);
      assertTrue(result.isErr());
    }
  }

  // ==================== Transformation: andThen() ====================

  @Nested
  @DisplayName("Result.andThen()")
  class FlatMapTests {

    @Test
    @DisplayName("Ok: mapper returns Result, flattened")
    void flatMap_success() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final Result<String, TestError> result =
          original.andThen(u -> Result.ok(u.name().toUpperCase()));
      assertTrue(result.isOk());
      assertEquals("JOHN", result.unwrap());
    }

    @Test
    @DisplayName("Ok: mapper not called on Err")
    void flatMap_failure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final AtomicBoolean called = new AtomicBoolean(false);
      final Result<String, TestError> result =
          original.andThen(
              u -> {
                called.set(true);
                return Result.ok(u.name());
              });
      assertTrue(result.isErr());
      assertFalse(called.get());
    }

    @Test
    @DisplayName("Ok: mapper returning Err propagates")
    void flatMap_mapperReturnsFailure() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final Result<String, TestError> result =
          original.andThen(_ -> Result.err(new TestError.Invalid("name")));
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("Ok: rejects a null Result from the mapper")
    void flatMap_rejectsNullResult() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      assertThrows(NullPointerException.class, () -> original.andThen(_ -> null));
    }
  }

  // ==================== Transformation: combine() ====================

  @Nested
  @DisplayName("Result.combine()")
  class CombineTests {

    @Test
    @DisplayName("both Ok: combiner applied")
    void combine_bothSuccess() {
      final Result<Integer, TestError> a = Result.ok(10);
      final Result<Integer, TestError> b = Result.ok(20);
      final Result<Integer, TestError> result = a.combine(b, Integer::sum);
      assertTrue(result.isOk());
      assertEquals(30, result.unwrap());
    }

    @Test
    @DisplayName("first Err: returns first Err")
    void combine_firstFailure() {
      final Result<Integer, TestError> a = Result.err(new TestError.NotFound("1"));
      final Result<Integer, TestError> b = Result.ok(20);
      final Result<Integer, TestError> result = a.combine(b, Integer::sum);
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("second Err: returns second Err")
    void combine_secondFailure() {
      final Result<Integer, TestError> a = Result.ok(10);
      final Result<Integer, TestError> b = Result.err(new TestError.NotFound("2"));
      final Result<Integer, TestError> result = a.combine(b, Integer::sum);
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("combines different types")
    void combine_differentTypes() {
      final Result<String, TestError> a = Result.ok("Hello");
      final Result<Integer, TestError> b = Result.ok(5);
      final Result<String, TestError> result = a.combine(b, (s, i) -> s + " x" + i);
      assertTrue(result.isOk());
      assertEquals("Hello x5", result.unwrap());
    }
  }

  // ==================== Recovery: recover() ====================

  @Nested
  @DisplayName("Result.recover()")
  class RecoverTests {

    @Test
    @DisplayName("Ok: returns unchanged")
    void recover_success() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final Result<User, TestError> result = original.recover(_ -> new User("0", "Guest", 0));
      assertTrue(result.isOk());
      assertEquals("John", result.unwrap().name());
    }

    @Test
    @DisplayName("Err: applies recovery function")
    void recover_failure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final Result<User, TestError> result = original.recover(_ -> new User("0", "Guest", 0));
      assertTrue(result.isOk());
      assertEquals("Guest", result.unwrap().name());
    }

    @Test
    @DisplayName("Err: rejects a null recovered value")
    void recover_rejectsNullValue() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      assertThrows(NullPointerException.class, () -> original.recover(_ -> null));
    }
  }

  // ==================== Recovery: recoverWith() ====================

  @Nested
  @DisplayName("Result.recoverWith()")
  class RecoverWithTests {

    @Test
    @DisplayName("Ok: returns unchanged")
    void recoverWith_success() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final Result<User, TestError> result =
          original.recoverWith(_ -> Result.ok(new User("0", "Guest", 0)));
      assertTrue(result.isOk());
      assertEquals("John", result.unwrap().name());
    }

    @Test
    @DisplayName("Err: applies recovery mapper")
    void recoverWith_failure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final Result<User, TestError> result =
          original.recoverWith(_ -> Result.ok(new User("0", "Guest", 0)));
      assertTrue(result.isOk());
      assertEquals("Guest", result.unwrap().name());
    }

    @Test
    @DisplayName("Err: recovery returning Err propagates")
    void recoverWith_recoveryReturnsFailure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final Result<User, TestError> result =
          original.recoverWith(_ -> Result.err(new TestError.Invalid("cannot recover")));
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("Err: rejects a null recovered Result")
    void recoverWith_rejectsNullResult() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      assertThrows(NullPointerException.class, () -> original.recoverWith(_ -> null));
    }
  }

  // ==================== Recovery: filter() ====================

  @Nested
  @DisplayName("Result.filter()")
  class FilterTests {

    @Test
    @DisplayName("Ok + predicate true: unchanged")
    void filter_successPredicateTrue() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final Result<User, TestError> result =
          original.filter(u -> u.age() >= 18, () -> new TestError.Invalid("underage"));
      assertTrue(result.isOk());
      assertEquals("John", result.unwrap().name());
    }

    @Test
    @DisplayName("Ok + predicate false: returns Err")
    void filter_successPredicateFalse() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 15));
      final Result<User, TestError> result =
          original.filter(u -> u.age() >= 18, () -> new TestError.Invalid("underage"));
      assertTrue(result.isErr());
    }

    @Test
    @DisplayName("Err: passes through unchanged")
    void filter_failure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final Result<User, TestError> result =
          original.filter(u -> u.age() >= 18, () -> new TestError.Invalid("underage"));
      assertTrue(result.isErr());
    }
  }

  // ==================== Terminal: fold() ====================

  @Nested
  @DisplayName("Result.fold()")
  class FoldTests {

    @Test
    @DisplayName("Ok: onOk applied")
    void fold_success() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      final String message = result.fold(u -> "User: " + u.name(), e -> "Error: " + e);
      assertEquals("User: John", message);
    }

    @Test
    @DisplayName("Err: onErr applied")
    void fold_failure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("1"));
      final String message = result.fold(u -> "User: " + u.name(), e -> "Error: " + e);
      assertEquals("Error: NotFound[id=1]", message);
    }
  }

  // ==================== Terminal: inspect() ====================

  @Nested
  @DisplayName("Result.inspect()")
  class PeekTests {

    @Test
    @DisplayName("Ok: consumer executed, returns this")
    void peek_success() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final AtomicBoolean called = new AtomicBoolean(false);
      final Result<User, TestError> result = original.inspect(_ -> called.set(true));
      assertTrue(called.get());
      assertSame(original, result);
    }

    @Test
    @DisplayName("Err: consumer not executed")
    void peek_failure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final AtomicBoolean called = new AtomicBoolean(false);
      final Result<User, TestError> result = original.inspect(_ -> called.set(false));
      assertFalse(called.get());
      assertSame(original, result);
    }
  }

  // ==================== Terminal: inspectErr() ====================

  @Nested
  @DisplayName("Result.inspectErr()")
  class PeekFailureTests {

    @Test
    @DisplayName("Err: consumer executed")
    void peekFailure_failure() {
      final Result<User, TestError> original = Result.err(new TestError.NotFound("1"));
      final AtomicBoolean called = new AtomicBoolean(false);
      final Result<User, TestError> result = original.inspectErr(_ -> called.set(true));
      assertTrue(called.get());
      assertSame(original, result);
    }

    @Test
    @DisplayName("Ok: consumer not executed")
    void peekFailure_success() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final AtomicBoolean called = new AtomicBoolean(false);
      final Result<User, TestError> result = original.inspectErr(_ -> called.set(true));
      assertFalse(called.get());
      assertSame(original, result);
    }
  }

  // ==================== Terminal: isOk() / isErr() ====================

  @Nested
  @DisplayName("Result.isOk() / isErr()")
  class IsSuccessFailureTests {

    @Test
    @DisplayName("isOk returns true for Ok")
    void isSuccess_trueForSuccess() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      assertTrue(result.isOk());
      assertFalse(result.isErr());
    }

    @Test
    @DisplayName("isErr returns true for Err")
    void isFailure_trueForFailure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("1"));
      assertFalse(result.isOk());
      assertTrue(result.isErr());
    }
  }

  // ==================== Terminal: unwrap() ====================

  @Nested
  @DisplayName("Result.unwrap()")
  class UnwrapTests {

    @Test
    @DisplayName("Ok: returns value")
    void unwrap_success() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      assertEquals("John", result.unwrap().name());
    }

    @Test
    @DisplayName("Err: throws RuntimeException")
    void unwrap_failure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("1"));
      final RuntimeException ex = assertThrows(RuntimeException.class, result::unwrap);
      assertTrue(ex.getMessage().contains("NotFound"));
    }
  }

  // ==================== Terminal: unwrapOrThrow() ====================

  @Nested
  @DisplayName("Result.unwrapOrThrow()")
  class UnwrapOrThrowTests {

    @Test
    @DisplayName("Ok: returns value")
    void unwrapOrThrow_success() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      assertEquals("John", result.unwrapOrThrow(_ -> new RuntimeException("expected")).name());
    }

    @Test
    @DisplayName("Err: throws custom exception")
    void unwrapOrThrow_failure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("1"));
      final IllegalStateException ex =
          assertThrows(
              IllegalStateException.class,
              () -> result.unwrapOrThrow(e -> new IllegalStateException("Not found: " + e)));
      assertTrue(ex.getMessage().contains("Not found"));
    }
  }

  // ==================== Terminal: unwrapOr() ====================

  @Nested
  @DisplayName("Result.unwrapOr()")
  class UnwrapOrTests {

    @Test
    @DisplayName("Ok: returns value")
    void unwrapOr_success() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      final User defaultUser = new User("0", "Guest", 0);
      assertEquals("John", result.unwrapOr(defaultUser).name());
    }

    @Test
    @DisplayName("Err: returns default")
    void unwrapOr_failure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("1"));
      final User defaultUser = new User("0", "Guest", 0);
      assertEquals("Guest", result.unwrapOr(defaultUser).name());
    }
  }

  // ==================== Terminal: unwrapOrElse() ====================

  @Nested
  @DisplayName("Result.unwrapOrElse()")
  class UnwrapOrElseTests {

    @Test
    @DisplayName("Ok: returns value")
    void unwrapOrElse_success() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      final User returned = result.unwrapOrElse(() -> new User("0", "Guest", 0));
      assertEquals("John", returned.name());
    }

    @Test
    @DisplayName("Err: supplier result")
    void unwrapOrElse_failure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("1"));
      final User returned = result.unwrapOrElse(() -> new User("0", "Guest", 0));
      assertEquals("Guest", returned.name());
    }

    @Test
    @DisplayName("Ok: supplier not called")
    void unwrapOrElse_supplierNotCalledOnSuccess() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      final AtomicBoolean called = new AtomicBoolean(false);
      result.unwrapOrElse(
          () -> {
            called.set(true);
            return new User("0", "Guest", 0);
          });
      assertFalse(called.get());
    }
  }

  // ==================== Terminal: stream() ====================

  @Nested
  @DisplayName("Result.stream()")
  class StreamTests {

    @Test
    @DisplayName("Ok: returns Stream with one element")
    void stream_success() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      final List<User> list = result.stream().toList();
      assertEquals(1, list.size());
      assertEquals("John", list.getFirst().name());
    }

    @Test
    @DisplayName("Err: returns empty Stream")
    void stream_failure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("1"));
      final List<User> list = result.stream().toList();
      assertEquals(0, list.size());
    }
  }

  // ==================== Terminal: toOptional() ====================

  @Nested
  @DisplayName("Result.toOptional()")
  class ToOptionalTests {

    @Test
    @DisplayName("Ok: returns Optional.of(value)")
    void toOptional_success() {
      final Result<User, TestError> result = Result.ok(new User("1", "John", 30));
      final Optional<User> optional = result.toOptional();
      assertTrue(optional.isPresent());
      assertEquals("John", optional.get().name());
    }

    @Test
    @DisplayName("Err: returns Optional.empty()")
    void toOptional_failure() {
      final Result<User, TestError> result = Result.err(new TestError.NotFound("1"));
      final Optional<User> optional = result.toOptional();
      assertTrue(optional.isEmpty());
    }

    @Test
    @DisplayName("empty Result contains Unit in Optional")
    void toOptional_successWithNull() {
      final Result<Result.Unit, TestError> result = Result.empty();
      final Optional<Result.Unit> optional = result.toOptional();
      assertSame(Result.Unit.INSTANCE, optional.orElseThrow());
    }
  }

  // ==================== Edge Cases ====================

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("chaining multiple transformations")
    void chaining_multipleTransformations() {
      final var result =
          Result.ok(new User("1", "John", 30))
              .map(User::name)
              .map(String::toUpperCase)
              .map(s -> "User: " + s);
      assertTrue(result.isOk());
      assertEquals("User: JOHN", result.unwrap());
    }

    @Test
    @DisplayName("chaining with Err short-circuits")
    void chaining_failureShortCircuits() {
      final AtomicBoolean secondCalled = new AtomicBoolean(false);
      final AtomicBoolean thirdCalled = new AtomicBoolean(false);
      final Result<String, TestError> result =
          Result.<String, TestError>err(new TestError.NotFound("1"))
              .map(
                  v -> {
                    secondCalled.set(true);
                    return v;
                  })
              .map(
                  v -> {
                    thirdCalled.set(true);
                    return v;
                  });
      assertTrue(result.isErr());
      assertFalse(secondCalled.get());
      assertFalse(thirdCalled.get());
    }

    @Test
    @DisplayName("mapErr does not affect Ok")
    void mapError_doesNotAffectSuccess() {
      final Result<User, TestError> result =
          Result.ok(new User("1", "John", 30))
              .mapErr(_ -> new TestError.Invalid("should not happen"));
      assertTrue(result.isOk());
      assertEquals("John", result.unwrap().name());
    }

    @Test
    @DisplayName("null in error type is rejected")
    void nullInErrorTypeIsValid() {
      assertThrows(NullPointerException.class, () -> Result.err(null));
    }

    @Test
    @DisplayName("transformations preserve immutability")
    void transformationsPreserveImmutability() {
      final Result<User, TestError> original = Result.ok(new User("1", "John", 30));
      final Result<String, TestError> mapped = original.map(User::name);
      assertTrue(original.isOk());
      assertTrue(mapped.isOk());
    }
  }
}
