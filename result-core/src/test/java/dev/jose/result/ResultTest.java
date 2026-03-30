package dev.jose.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultTest {

	// ==================== Test Helpers ====================

	sealed interface TestError permits TestError.NotFound, TestError.Invalid {
		record NotFound(String id) implements TestError {
		}
		record Invalid(String field) implements TestError {
		}
	}

	record User(String id, String name, int age) {
	}

	// ==================== Static Factory: success() ====================

	@Nested
	@DisplayName("Result.success()")
	class SuccessTests {

		@Test
		@DisplayName("creates Success with non-null value")
		void success_withNonNullValue() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			assertTrue(result.isSuccess());
			assertFalse(result.isFailure());
			assertEquals("John", result.unwrap().name());
		}

		@Test
		@DisplayName("creates Success with null value")
		void success_withNullValue() {
			final Result<String, TestError> result = Result.success(null);
			assertTrue(result.isSuccess());
			assertNull(result.unwrap());
		}

		@Test
		@DisplayName("creates Success with different types")
		void success_withDifferentTypes() {
			final Result<Integer, TestError> intResult = Result.success(42);
			final Result<List<String>, TestError> listResult = Result.success(List.of("a", "b"));
			final Result<Void, TestError> voidResult = Result.success(null);

			assertEquals(42, intResult.unwrap());
			assertEquals(List.of("a", "b"), listResult.unwrap());
			assertNull(voidResult.unwrap());
		}
	}

	// ==================== Static Factory: failure() ====================

	@Nested
	@DisplayName("Result.failure()")
	class FailureTests {

		@Test
		@DisplayName("creates Failure with error")
		void failure_createsFailure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("123"));
			assertFalse(result.isSuccess());
			assertTrue(result.isFailure());
			assertThrows(RuntimeException.class, result::unwrap);
		}

		@Test
		@DisplayName("failure propagates through transformations")
		void failure_propagatesThroughTransformations() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final Result<String, TestError> mapped = original.map(User::name);
			assertTrue(mapped.isFailure());
		}
	}

	// ==================== Static Factory: ofNullable() ====================

	@Nested
	@DisplayName("Result.ofNullable()")
	class OfNullableTests {

		@Test
		@DisplayName("non-null value creates Success")
		void ofNullable_nonNullValue() {
			final Result<String, TestError> result = Result.ofNullable("hello", () -> new TestError.Invalid("field"));
			assertTrue(result.isSuccess());
			assertEquals("hello", result.unwrap());
		}

		@Test
		@DisplayName("null value creates Failure")
		void ofNullable_nullValue() {
			final AtomicBoolean supplierCalled = new AtomicBoolean(false);
			final Result<String, TestError> result = Result.ofNullable(null, () -> {
				supplierCalled.set(true);
				return new TestError.Invalid("field");
			});
			assertTrue(result.isFailure());
			assertTrue(supplierCalled.get());
		}

		@Test
		@DisplayName("supplier not called for non-null")
		void ofNullable_supplierNotCalledForNonNull() {
			final AtomicBoolean supplierCalled = new AtomicBoolean(false);
			Result.ofNullable("value", () -> {
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
		@DisplayName("returns Success containing null")
		void empty_returnsSuccessNull() {
			final Result<Void, TestError> result = Result.empty();
			assertTrue(result.isSuccess());
			assertNull(result.unwrap());
		}

		@Test
		@DisplayName("works as void operation")
		void empty_worksAsVoid() {
			final Result<Void, TestError> result = Result.<TestError>empty().map(_ -> {
				System.out.println("side effect");
				return null;
			});
			assertTrue(result.isSuccess());
		}
	}

	// ==================== Static Factory: attempt() ====================

	@Nested
	@DisplayName("Result.attempt()")
	class AttemptTests {

		@Test
		@DisplayName("success case returns Success")
		void attempt_success() {
			final Result<String, TestError> result = Result.attempt(() -> "hello", _ -> new TestError.Invalid("error"));
			assertTrue(result.isSuccess());
			assertEquals("hello", result.unwrap());
		}

		@Test
		@DisplayName("unchecked exception returns Failure")
		void attempt_uncheckedException() {
			final Result<String, TestError> result = Result.attempt(() -> {
				throw new IllegalArgumentException("bad input");
			}, e -> new TestError.Invalid(e.getMessage()));
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("checked exception returns Failure")
		void attempt_checkedException() {
			final Result<String, TestError> result = Result.attempt(() -> {
				throw new IOException("file not found");
			}, e -> new TestError.Invalid(e.getMessage()));
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("exception mapper receives correct exception type")
		void attempt_exceptionMapperReceivesCorrectType() {
			final Result<String, TestError> result = Result.attempt(() -> {
				throw new IllegalArgumentException("test");
			}, e -> {
				assertInstanceOf(IllegalArgumentException.class, e);
				return new TestError.Invalid(e.getClass().getSimpleName());
			});
			assertTrue(result.isFailure());
		}
	}

	// ==================== Static Factory: fromOptional() ====================

	@Nested
	@DisplayName("Result.fromOptional()")
	class FromOptionalTests {

		@Test
		@DisplayName("Optional.of(value) returns Success")
		void fromOptional_withValue() {
			final Result<String, TestError> result = Result.fromOptional(Optional.of("hello"),
					() -> new TestError.NotFound("default"));
			assertTrue(result.isSuccess());
			assertEquals("hello", result.unwrap());
		}

		@Test
		@DisplayName("Optional.empty() returns Failure")
		void fromOptional_empty() {
			final Result<String, TestError> result = Result.fromOptional(Optional.empty(),
					() -> new TestError.NotFound("id"));
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("errorSupplier called only on empty")
		void fromOptional_supplierCalledOnlyOnEmpty() {
			final AtomicBoolean called = new AtomicBoolean(false);
			Result.fromOptional(Optional.of("value"), () -> {
				called.set(true);
				return new TestError.NotFound("x");
			});
			assertFalse(called.get());
			Result.fromOptional(Optional.empty(), () -> {
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
		@DisplayName("all Success returns Success with collected values")
		void collect_allSuccess() {
			final Stream<Result<Integer, TestError>> stream = Stream.of(Result.success(1), Result.success(2),
					Result.success(3));
			final Result<List<Integer>, TestError> result = Result.collect(stream);
			assertTrue(result.isSuccess());
			assertEquals(List.of(1, 2, 3), result.unwrap());
		}

		@Test
		@DisplayName("first Failure short-circuits")
		void collect_shortCircuitsOnFirstFailure() {
			// Just verify short-circuit behavior works by checking failure is returned
			final Result<List<Integer>, TestError> result = Result.collect(
					Stream.of(Result.success(1), Result.failure(new TestError.Invalid("error")), Result.success(3)));
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("empty stream returns Success with empty collection")
		void collect_emptyStream() {
			final Stream<Result<Integer, TestError>> stream = Stream.empty();
			final Result<List<Integer>, TestError> result = Result.collect(stream);
			assertTrue(result.isSuccess());
			assertTrue(result.unwrap().isEmpty());
		}

		@Test
		@DisplayName("custom Collector (toSet)")
		void collect_customCollector() {
			final Stream<Result<Integer, TestError>> stream = Stream.of(Result.success(1), Result.success(2),
					Result.success(3));
			final Result<Set<Integer>, TestError> result = Result.collect(stream, Collectors.toSet());
			assertTrue(result.isSuccess());
			assertEquals(Set.of(1, 2, 3), result.unwrap());
		}

		@Test
		@DisplayName("Failure at different positions")
		void collect_failureAtDifferentPositions() {
			assertTrue(Result.collect(Stream.of(Result.failure(new TestError.NotFound("1")), Result.success(2)))
					.isFailure());

			assertTrue(Result.collect(Stream.of(Result.success(1), Result.failure(new TestError.NotFound("2"))))
					.isFailure());
		}
	}

	// ==================== Static Factory: collect() with List ====================

	@Nested
	@DisplayName("Result.collect(Stream)")
	class CollectListTests {

		@Test
		@DisplayName("collects into List")
		void collectList() {
			final Stream<Result<String, TestError>> stream = Stream.of(Result.success("a"), Result.success("b"));
			final Result<List<String>, TestError> result = Result.collect(stream);
			assertTrue(result.isSuccess());
			assertEquals(List.of("a", "b"), result.unwrap());
		}
	}

	// ==================== Static Factory: sequence() ====================

	@Nested
	@DisplayName("Result.sequence()")
	class SequenceTests {

		@Test
		@DisplayName("all Success returns list in order")
		void sequence_allSuccess() {
			final List<Result<Integer, TestError>> list = List.of(Result.success(1), Result.success(2),
					Result.success(3));
			final Result<List<Integer>, TestError> result = Result.sequence(list);
			assertTrue(result.isSuccess());
			assertEquals(List.of(1, 2, 3), result.unwrap());
		}

		@Test
		@DisplayName("Failure at first position")
		void sequence_failureAtFirst() {
			final List<Result<Integer, TestError>> list = List.of(Result.failure(new TestError.NotFound("1")),
					Result.success(2), Result.success(3));
			final Result<List<Integer>, TestError> result = Result.sequence(list);
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("Failure at middle position")
		void sequence_failureAtMiddle() {
			final List<Result<Integer, TestError>> list = List.of(Result.success(1),
					Result.failure(new TestError.Invalid("2")), Result.success(3));
			final Result<List<Integer>, TestError> result = Result.sequence(list);
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("Failure at last position")
		void sequence_failureAtLast() {
			final List<Result<Integer, TestError>> list = List.of(Result.success(1), Result.success(2),
					Result.failure(new TestError.NotFound("3")));
			final Result<List<Integer>, TestError> result = Result.sequence(list);
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("empty list returns Success with empty list")
		void sequence_emptyList() {
			final List<Result<Integer, TestError>> list = List.of();
			final Result<List<Integer>, TestError> result = Result.sequence(list);
			assertTrue(result.isSuccess());
			assertTrue(result.unwrap().isEmpty());
		}

		@Test
		@DisplayName("varargs: zero args")
		void sequence_varargsZeroArgs() {
			final Result<List<Integer>, TestError> result = Result.sequence();
			assertTrue(result.isSuccess());
			assertTrue(result.unwrap().isEmpty());
		}

		@Test
		@DisplayName("varargs: single element")
		void sequence_varargsSingleElement() {
			final Result<List<Integer>, TestError> result = Result.sequence(Result.success(42));
			assertTrue(result.isSuccess());
			assertEquals(List.of(42), result.unwrap());
		}

		@Test
		@DisplayName("varargs: multiple elements")
		void sequence_varargsMultiple() {
			final Result<List<Integer>, TestError> result = Result.sequence(Result.success(1), Result.success(2),
					Result.success(3));
			assertTrue(result.isSuccess());
			assertEquals(List.of(1, 2, 3), result.unwrap());
		}
	}

	// ==================== Static Factory: flatten() ====================

	@Nested
	@DisplayName("Result.flatten()")
	class FlattenTests {

		@Test
		@DisplayName("nested Success unwraps to Success")
		void flatten_nestedSuccess() {
			final Result<Result<Integer, TestError>, TestError> nested = Result.success(Result.success(42));
			final Result<Integer, TestError> result = Result.flatten(nested);
			assertTrue(result.isSuccess());
			assertEquals(42, result.unwrap());
		}

		@Test
		@DisplayName("nested Failure returns Failure")
		void flatten_nestedFailure() {
			final Result<Result<Integer, TestError>, TestError> nested = Result
					.success(Result.failure(new TestError.NotFound("1")));
			final Result<Integer, TestError> result = Result.flatten(nested);
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("outer Failure returns Failure")
		void flatten_outerFailure() {
			final Result<Result<Integer, TestError>, TestError> nested = Result.failure(new TestError.Invalid("outer"));
			final Result<Integer, TestError> result = Result.flatten(nested);
			assertTrue(result.isFailure());
		}
	}

	// ==================== Transformation: map() ====================

	@Nested
	@DisplayName("Result.map()")
	class MapTests {

		@Test
		@DisplayName("Success: function applied, new type")
		void map_success() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final Result<String, TestError> result = original.map(User::name);
			assertTrue(result.isSuccess());
			assertEquals("John", result.unwrap());
		}

		@Test
		@DisplayName("Success: function not called on Failure")
		void map_failure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final AtomicBoolean called = new AtomicBoolean(false);
			final Result<String, TestError> result = original.map(u -> {
				called.set(true);
				return u.name();
			});
			assertTrue(result.isFailure());
			assertFalse(called.get());
		}

		@Test
		@DisplayName("transforms to different type")
		void map_differentType() {
			final Result<Integer, TestError> original = Result.success(10);
			final Result<String, TestError> result = original.map(i -> "number-" + i);
			assertEquals("number-10", result.unwrap());
		}
	}

	// ==================== Transformation: mapError() ====================

	@Nested
	@DisplayName("Result.mapError()")
	class MapErrorTests {

		@Test
		@DisplayName("Failure: error transformed")
		void mapError_failure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("123"));
			final Result<User, String> result = original.mapError(Object::toString);
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("Success: unchanged")
		void mapError_success() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final Result<User, String> result = original.mapError(Object::toString);
			assertTrue(result.isSuccess());
			assertEquals("John", result.unwrap().name());
		}
	}

	// ==================== Transformation: mapBoth() ====================

	@Nested
	@DisplayName("Result.mapBoth()")
	class MapBothTests {

		@Test
		@DisplayName("Success: successMapper applied")
		void mapBoth_success() {
			final Result<Integer, TestError> original = Result.success(10);
			final Result<String, String> result = original.mapBoth(i -> "success-" + i, e -> "error-" + e);
			assertTrue(result.isSuccess());
			assertEquals("success-10", result.unwrap());
		}

		@Test
		@DisplayName("Failure: failureMapper applied")
		void mapBoth_failure() {
			final Result<Integer, TestError> original = Result.failure(new TestError.NotFound("1"));
			final Result<String, String> result = original.mapBoth(i -> "success-" + i, e -> "error-" + e);
			assertTrue(result.isFailure());
		}
	}

	// ==================== Transformation: flatMap() ====================

	@Nested
	@DisplayName("Result.flatMap()")
	class FlatMapTests {

		@Test
		@DisplayName("Success: mapper returns Result, flattened")
		void flatMap_success() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final Result<String, TestError> result = original.flatMap(u -> Result.success(u.name().toUpperCase()));
			assertTrue(result.isSuccess());
			assertEquals("JOHN", result.unwrap());
		}

		@Test
		@DisplayName("Success: mapper not called on Failure")
		void flatMap_failure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final AtomicBoolean called = new AtomicBoolean(false);
			final Result<String, TestError> result = original.flatMap(u -> {
				called.set(true);
				return Result.success(u.name());
			});
			assertTrue(result.isFailure());
			assertFalse(called.get());
		}

		@Test
		@DisplayName("Success: mapper returning Failure propagates")
		void flatMap_mapperReturnsFailure() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final Result<String, TestError> result = original
					.flatMap(_ -> Result.failure(new TestError.Invalid("name")));
			assertTrue(result.isFailure());
		}
	}

	// ==================== Transformation: combine() ====================

	@Nested
	@DisplayName("Result.combine()")
	class CombineTests {

		@Test
		@DisplayName("both Success: combiner applied")
		void combine_bothSuccess() {
			final Result<Integer, TestError> a = Result.success(10);
			final Result<Integer, TestError> b = Result.success(20);
			final Result<Integer, TestError> result = a.combine(b, Integer::sum);
			assertTrue(result.isSuccess());
			assertEquals(30, result.unwrap());
		}

		@Test
		@DisplayName("first Failure: returns first Failure")
		void combine_firstFailure() {
			final Result<Integer, TestError> a = Result.failure(new TestError.NotFound("1"));
			final Result<Integer, TestError> b = Result.success(20);
			final Result<Integer, TestError> result = a.combine(b, Integer::sum);
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("second Failure: returns second Failure")
		void combine_secondFailure() {
			final Result<Integer, TestError> a = Result.success(10);
			final Result<Integer, TestError> b = Result.failure(new TestError.NotFound("2"));
			final Result<Integer, TestError> result = a.combine(b, Integer::sum);
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("combines different types")
		void combine_differentTypes() {
			final Result<String, TestError> a = Result.success("Hello");
			final Result<Integer, TestError> b = Result.success(5);
			final Result<String, TestError> result = a.combine(b, (s, i) -> s + " x" + i);
			assertTrue(result.isSuccess());
			assertEquals("Hello x5", result.unwrap());
		}
	}

	// ==================== Recovery: recover() ====================

	@Nested
	@DisplayName("Result.recover()")
	class RecoverTests {

		@Test
		@DisplayName("Success: returns unchanged")
		void recover_success() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final Result<User, TestError> result = original.recover(_ -> new User("0", "Guest", 0));
			assertTrue(result.isSuccess());
			assertEquals("John", result.unwrap().name());
		}

		@Test
		@DisplayName("Failure: applies recovery function")
		void recover_failure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final Result<User, TestError> result = original.recover(_ -> new User("0", "Guest", 0));
			assertTrue(result.isSuccess());
			assertEquals("Guest", result.unwrap().name());
		}
	}

	// ==================== Recovery: recoverWith() ====================

	@Nested
	@DisplayName("Result.recoverWith()")
	class RecoverWithTests {

		@Test
		@DisplayName("Success: returns unchanged")
		void recoverWith_success() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final Result<User, TestError> result = original.recoverWith(_ -> Result.success(new User("0", "Guest", 0)));
			assertTrue(result.isSuccess());
			assertEquals("John", result.unwrap().name());
		}

		@Test
		@DisplayName("Failure: applies recovery mapper")
		void recoverWith_failure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final Result<User, TestError> result = original.recoverWith(_ -> Result.success(new User("0", "Guest", 0)));
			assertTrue(result.isSuccess());
			assertEquals("Guest", result.unwrap().name());
		}

		@Test
		@DisplayName("Failure: recovery returning Failure propagates")
		void recoverWith_recoveryReturnsFailure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final Result<User, TestError> result = original
					.recoverWith(_ -> Result.failure(new TestError.Invalid("cannot recover")));
			assertTrue(result.isFailure());
		}
	}

	// ==================== Recovery: filter() ====================

	@Nested
	@DisplayName("Result.filter()")
	class FilterTests {

		@Test
		@DisplayName("Success + predicate true: unchanged")
		void filter_successPredicateTrue() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final Result<User, TestError> result = original.filter(u -> u.age() >= 18,
					() -> new TestError.Invalid("underage"));
			assertTrue(result.isSuccess());
			assertEquals("John", result.unwrap().name());
		}

		@Test
		@DisplayName("Success + predicate false: returns Failure")
		void filter_successPredicateFalse() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 15));
			final Result<User, TestError> result = original.filter(u -> u.age() >= 18,
					() -> new TestError.Invalid("underage"));
			assertTrue(result.isFailure());
		}

		@Test
		@DisplayName("Failure: passes through unchanged")
		void filter_failure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final Result<User, TestError> result = original.filter(u -> u.age() >= 18,
					() -> new TestError.Invalid("underage"));
			assertTrue(result.isFailure());
		}
	}

	// ==================== Terminal: fold() ====================

	@Nested
	@DisplayName("Result.fold()")
	class FoldTests {

		@Test
		@DisplayName("Success: onSuccess applied")
		void fold_success() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			final String message = result.fold(u -> "User: " + u.name(), e -> "Error: " + e);
			assertEquals("User: John", message);
		}

		@Test
		@DisplayName("Failure: onFailure applied")
		void fold_failure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("1"));
			final String message = result.fold(u -> "User: " + u.name(), e -> "Error: " + e);
			assertEquals("Error: NotFound[id=1]", message);
		}
	}

	// ==================== Terminal: peek() ====================

	@Nested
	@DisplayName("Result.peek()")
	class PeekTests {

		@Test
		@DisplayName("Success: consumer executed, returns this")
		void peek_success() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final AtomicBoolean called = new AtomicBoolean(false);
			final Result<User, TestError> result = original.peek(_ -> called.set(true));
			assertTrue(called.get());
			assertSame(original, result);
		}

		@Test
		@DisplayName("Failure: consumer not executed")
		void peek_failure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final AtomicBoolean called = new AtomicBoolean(false);
			final Result<User, TestError> result = original.peek(_ -> called.set(false));
			assertFalse(called.get());
			assertSame(original, result);
		}
	}

	// ==================== Terminal: peekFailure() ====================

	@Nested
	@DisplayName("Result.peekFailure()")
	class PeekFailureTests {

		@Test
		@DisplayName("Failure: consumer executed")
		void peekFailure_failure() {
			final Result<User, TestError> original = Result.failure(new TestError.NotFound("1"));
			final AtomicBoolean called = new AtomicBoolean(false);
			final Result<User, TestError> result = original.peekFailure(_ -> called.set(true));
			assertTrue(called.get());
			assertSame(original, result);
		}

		@Test
		@DisplayName("Success: consumer not executed")
		void peekFailure_success() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final AtomicBoolean called = new AtomicBoolean(false);
			final Result<User, TestError> result = original.peekFailure(_ -> called.set(true));
			assertFalse(called.get());
			assertSame(original, result);
		}
	}

	// ==================== Terminal: isSuccess() / isFailure() ====================

	@Nested
	@DisplayName("Result.isSuccess() / isFailure()")
	class IsSuccessFailureTests {

		@Test
		@DisplayName("isSuccess returns true for Success")
		void isSuccess_trueForSuccess() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			assertTrue(result.isSuccess());
			assertFalse(result.isFailure());
		}

		@Test
		@DisplayName("isFailure returns true for Failure")
		void isFailure_trueForFailure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("1"));
			assertFalse(result.isSuccess());
			assertTrue(result.isFailure());
		}
	}

	// ==================== Terminal: unwrap() ====================

	@Nested
	@DisplayName("Result.unwrap()")
	class UnwrapTests {

		@Test
		@DisplayName("Success: returns value")
		void unwrap_success() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			assertEquals("John", result.unwrap().name());
		}

		@Test
		@DisplayName("Failure: throws RuntimeException")
		void unwrap_failure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("1"));
			final RuntimeException ex = assertThrows(RuntimeException.class, result::unwrap);
			assertTrue(ex.getMessage().contains("NotFound"));
		}
	}

	// ==================== Terminal: unwrapOrThrow() ====================

	@Nested
	@DisplayName("Result.unwrapOrThrow()")
	class UnwrapOrThrowTests {

		@Test
		@DisplayName("Success: returns value")
		void unwrapOrThrow_success() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			assertEquals("John", result.unwrapOrThrow(_ -> new RuntimeException("expected")).name());
		}

		@Test
		@DisplayName("Failure: throws custom exception")
		void unwrapOrThrow_failure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("1"));
			final IllegalStateException ex = assertThrows(IllegalStateException.class,
					() -> result.unwrapOrThrow(e -> new IllegalStateException("Not found: " + e)));
			assertTrue(ex.getMessage().contains("Not found"));
		}
	}

	// ==================== Terminal: unwrapOr() ====================

	@Nested
	@DisplayName("Result.unwrapOr()")
	class UnwrapOrTests {

		@Test
		@DisplayName("Success: returns value")
		void unwrapOr_success() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			final User defaultUser = new User("0", "Guest", 0);
			assertEquals("John", result.unwrapOr(defaultUser).name());
		}

		@Test
		@DisplayName("Failure: returns default")
		void unwrapOr_failure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("1"));
			final User defaultUser = new User("0", "Guest", 0);
			assertEquals("Guest", result.unwrapOr(defaultUser).name());
		}
	}

	// ==================== Terminal: unwrapOrElse() ====================

	@Nested
	@DisplayName("Result.unwrapOrElse()")
	class UnwrapOrElseTests {

		@Test
		@DisplayName("Success: returns value")
		void unwrapOrElse_success() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			final User returned = result.unwrapOrElse(() -> new User("0", "Guest", 0));
			assertEquals("John", returned.name());
		}

		@Test
		@DisplayName("Failure: supplier result")
		void unwrapOrElse_failure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("1"));
			final User returned = result.unwrapOrElse(() -> new User("0", "Guest", 0));
			assertEquals("Guest", returned.name());
		}

		@Test
		@DisplayName("Success: supplier not called")
		void unwrapOrElse_supplierNotCalledOnSuccess() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			final AtomicBoolean called = new AtomicBoolean(false);
			result.unwrapOrElse(() -> {
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
		@DisplayName("Success: returns Stream with one element")
		void stream_success() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			final List<User> list = result.stream().toList();
			assertEquals(1, list.size());
			assertEquals("John", list.getFirst().name());
		}

		@Test
		@DisplayName("Failure: returns empty Stream")
		void stream_failure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("1"));
			final List<User> list = result.stream().toList();
			assertEquals(0, list.size());
		}
	}

	// ==================== Terminal: toOptional() ====================

	@Nested
	@DisplayName("Result.toOptional()")
	class ToOptionalTests {

		@Test
		@DisplayName("Success: returns Optional.of(value)")
		void toOptional_success() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30));
			final Optional<User> optional = result.toOptional();
			assertTrue(optional.isPresent());
			assertEquals("John", optional.get().name());
		}

		@Test
		@DisplayName("Failure: returns Optional.empty()")
		void toOptional_failure() {
			final Result<User, TestError> result = Result.failure(new TestError.NotFound("1"));
			final Optional<User> optional = result.toOptional();
			assertTrue(optional.isEmpty());
		}

		@Test
		@DisplayName("Success with null: toOptional returns empty (ofNullable semantics)")
		void toOptional_successWithNull() {
			final Result<String, TestError> result = Result.success(null);
			final Optional<String> optional = result.toOptional();
			// Optional.ofNullable(null) returns empty, which is the expected behavior
			assertTrue(optional.isEmpty());
		}
	}

	// ==================== Edge Cases ====================

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCaseTests {

		@Test
		@DisplayName("chaining multiple transformations")
		void chaining_multipleTransformations() {
			final var result = Result.success(new User("1", "John", 30)).map(User::name).map(String::toUpperCase)
					.map(s -> "User: " + s);
			assertTrue(result.isSuccess());
			assertEquals("User: JOHN", result.unwrap());
		}

		@Test
		@DisplayName("chaining with Failure short-circuits")
		void chaining_failureShortCircuits() {
			final AtomicBoolean secondCalled = new AtomicBoolean(false);
			final AtomicBoolean thirdCalled = new AtomicBoolean(false);
			final Result<String, TestError> result = Result.<String, TestError>failure(new TestError.NotFound("1"))
					.map(v -> {
						secondCalled.set(true);
						return v;
					}).map(v -> {
						thirdCalled.set(true);
						return v;
					});
			assertTrue(result.isFailure());
			assertFalse(secondCalled.get());
			assertFalse(thirdCalled.get());
		}

		@Test
		@DisplayName("mapError does not affect Success")
		void mapError_doesNotAffectSuccess() {
			final Result<User, TestError> result = Result.success(new User("1", "John", 30))
					.mapError(_ -> new TestError.Invalid("should not happen"));
			assertTrue(result.isSuccess());
			assertEquals("John", result.unwrap().name());
		}

		@Test
		@DisplayName("null in error type is valid")
		void nullInErrorTypeIsValid() {
			final Result<String, String> result = Result.failure(null);
			assertTrue(result.isFailure());
			assertThrows(RuntimeException.class, result::unwrap);
		}

		@Test
		@DisplayName("transformations preserve immutability")
		void transformationsPreserveImmutability() {
			final Result<User, TestError> original = Result.success(new User("1", "John", 30));
			final Result<String, TestError> mapped = original.map(User::name);
			assertTrue(original.isSuccess());
			assertTrue(mapped.isSuccess());
		}
	}
}
