package dev.jose.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncResultTest {

	// ==================== Test Helpers ====================

	sealed interface TestError permits TestError.NotFound, TestError.Invalid {
		record NotFound(String id) implements TestError {
		}
		record Invalid(String field) implements TestError {
		}
	}

	record User(String id, String name, int age) {
	}

	// ==================== Static Factory: of() ====================

	@Nested
	@DisplayName("AsyncResult.of()")
	class OfTests {

		@Test
		@DisplayName("already completed future returns Completed")
		void of_alreadyCompleted() {
			final CompletableFuture<Result<User, TestError>> future = CompletableFuture
					.completedFuture(Result.success(new User("1", "John", 30)));
			final AsyncResult<User, TestError> result = AsyncResult.of(future);

			assertInstanceOf(AsyncResult.Completed.class, result);
			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("pending future returns Pending")
		void of_pendingFuture() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> result = AsyncResult.of(future);

			assertInstanceOf(AsyncResult.Pending.class, result);
			assertFalse(result.isDone());

			future.complete(Result.success(new User("1", "John", 30)));
		}

		@Test
		@DisplayName("completed exceptionally returns Pending")
		void of_completedExceptionally() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			future.completeExceptionally(new RuntimeException("test"));

			final AsyncResult<User, TestError> result = AsyncResult.of(future);

			assertInstanceOf(AsyncResult.Pending.class, result);
		}
	}

	// ==================== Static Factory: fromStage() ====================

	@Nested
	@DisplayName("AsyncResult.fromStage()")
	class FromStageTests {

		@Test
		@DisplayName("wraps CompletionStage")
		void fromStage_basic() {
			final CompletableFuture<Result<User, TestError>> future = CompletableFuture
					.completedFuture(Result.success(new User("1", "John", 30)));

			final AsyncResult<User, TestError> result = AsyncResult.fromStage(future);

			assertInstanceOf(AsyncResult.Completed.class, result);
		}
	}

	// ==================== Static Factory: completed() ====================

	@Nested
	@DisplayName("AsyncResult.completed()")
	class CompletedTests {

		@Test
		@DisplayName("wraps Success")
		void completed_success() {
			final AsyncResult<User, TestError> result = AsyncResult
					.completed(Result.success(new User("1", "John", 30)));

			assertInstanceOf(AsyncResult.Completed.class, result);
			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("wraps Failure")
		void completed_failure() {
			final AsyncResult<User, TestError> result = AsyncResult
					.completed(Result.failure(new TestError.NotFound("1")));

			assertInstanceOf(AsyncResult.Completed.class, result);
			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Static Factory: attempt() ====================

	@Nested
	@DisplayName("AsyncResult.attempt()")
	class AttemptTests {

		@Test
		@DisplayName("success future returns Pending with Success")
		void attempt_success() {
			final CompletableFuture<String> future = CompletableFuture.completedFuture("hello");
			final AsyncResult<String, TestError> result = AsyncResult.attempt(future,
					e -> new TestError.Invalid(e.getMessage()));

			assertInstanceOf(AsyncResult.Pending.class, result);
			assertTrue(result.join().isSuccess());
			assertEquals("hello", result.join().unwrap());
		}

		@Test
		@DisplayName("failed future returns Pending with Failure")
		void attempt_failure() {
			final CompletableFuture<String> future = new CompletableFuture<>();
			future.completeExceptionally(new IllegalArgumentException("bad input"));

			final AsyncResult<String, TestError> result = AsyncResult.attempt(future,
					e -> new TestError.Invalid(e.getMessage()));

			assertInstanceOf(AsyncResult.Pending.class, result);
			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Static Factory: success() / failure()
	// ====================

	@Nested
	@DisplayName("AsyncResult.success() / failure()")
	class SuccessFailureTests {

		@Test
		@DisplayName("success creates Completed")
		void success_createsCompleted() {
			final AsyncResult<User, TestError> result = AsyncResult.success(new User("1", "John", 30));

			assertInstanceOf(AsyncResult.Completed.class, result);
			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("failure creates Completed with Failure")
		void failure_createsCompleted() {
			final AsyncResult<User, TestError> result = AsyncResult.failure(new TestError.NotFound("1"));

			assertInstanceOf(AsyncResult.Completed.class, result);
			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Static Factory: collectAll() ====================

	@Nested
	@DisplayName("AsyncResult.collectAll()")
	class CollectAllTests {

		@Test
		@DisplayName("empty list returns empty Success")
		void collectAll_empty() {
			final AsyncResult<List<Integer>, TestError> result = AsyncResult.collectAll(List.of());

			assertTrue(result.join().isSuccess());
			assertTrue(result.join().unwrap().isEmpty());
		}

		@Test
		@DisplayName("all Completed Success returns list")
		void collectAll_allCompleted() {
			final List<AsyncResult<Integer, TestError>> list = List.of(AsyncResult.success(1), AsyncResult.success(2),
					AsyncResult.success(3));

			final AsyncResult<List<Integer>, TestError> result = AsyncResult.collectAll(list);

			assertTrue(result.join().isSuccess());
			assertEquals(List.of(1, 2, 3), result.join().unwrap());
		}

		@Test
		@DisplayName("all Failures returns first Failure")
		void collectAll_allFailures() {
			final List<AsyncResult<Integer, TestError>> list = List.of(
					AsyncResult.failure(new TestError.Invalid("first")),
					AsyncResult.failure(new TestError.Invalid("second")),
					AsyncResult.failure(new TestError.Invalid("third")));

			final AsyncResult<List<Integer>, TestError> result = AsyncResult.collectAll(list);

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Static Factory: sequence() ====================

	@Nested
	@DisplayName("AsyncResult.sequence()")
	class SequenceTests {

		@Test
		@DisplayName("alias for collectAll")
		void sequence_alias() {
			final List<AsyncResult<Integer, TestError>> list = List.of(AsyncResult.success(1), AsyncResult.success(2));

			final AsyncResult<List<Integer>, TestError> result = AsyncResult.sequence(list);

			assertTrue(result.join().isSuccess());
			assertEquals(List.of(1, 2), result.join().unwrap());
		}
	}

	// ==================== Static Factory: race() ====================

	@Nested
	@DisplayName("AsyncResult.race()")
	class RaceTests {

		@Test
		@DisplayName("both Completed: first wins")
		void race_bothCompleted() {
			final AsyncResult<Integer, TestError> a = AsyncResult.success(1);
			final AsyncResult<Integer, TestError> b = AsyncResult.success(2);

			final AsyncResult<Integer, TestError> result = AsyncResult.race(a, b);

			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("first Failure wins")
		void race_firstFailureWins() {
			final AsyncResult<Integer, TestError> a = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<Integer, TestError> b = AsyncResult.success(2);

			final AsyncResult<Integer, TestError> result = AsyncResult.race(a, b);

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Static Factory: delay() ====================

	@Nested
	@DisplayName("AsyncResult.delay()")
	class DelayTests {

		@Test
		@DisplayName("Completed: returns Pending wrapper")
		@Timeout(value = 2, unit = SECONDS)
		void delay_completed() {
			final AsyncResult<User, TestError> result = AsyncResult
					.delay(AsyncResult.success(new User("1", "John", 30)), Duration.ofMillis(10));

			assertInstanceOf(AsyncResult.Pending.class, result);
			assertTrue(result.join().isSuccess());
		}
	}

	// ==================== Transformation: map() ====================

	@Nested
	@DisplayName("AsyncResult.map()")
	class MapTests {

		@Test
		@DisplayName("Completed Success: transforms value")
		void map_completedSuccess() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<String, TestError> result = original.map(User::name);

			assertTrue(result.join().isSuccess());
			assertEquals("John", result.join().unwrap());
		}

		@Test
		@DisplayName("Completed Failure: passes through")
		void map_completedFailure() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<String, TestError> result = original.map(User::name);

			assertTrue(result.join().isFailure());
		}

		@Test
		@DisplayName("Pending Success: transforms when complete")
		@Timeout(value = 2, unit = SECONDS)
		void map_pendingSuccess() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AsyncResult<String, TestError> result = original.map(User::name);

			future.complete(Result.success(new User("1", "John", 30)));

			assertTrue(result.join().isSuccess());
			assertEquals("John", result.join().unwrap());
		}

		@Test
		@DisplayName("Pending Failure: passes through")
		@Timeout(value = 2, unit = SECONDS)
		void map_pendingFailure() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AsyncResult<String, TestError> result = original.map(User::name);

			future.complete(Result.failure(new TestError.NotFound("1")));

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Transformation: mapError() ====================

	@Nested
	@DisplayName("AsyncResult.mapError()")
	class MapErrorTests {

		@Test
		@DisplayName("Success: unchanged")
		void mapError_success() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<User, String> result = original.mapError(Object::toString);

			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("Failure: transforms error")
		void mapError_failure() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("123"));
			final AsyncResult<User, String> result = original.mapError(Object::toString);

			assertTrue(result.join().isFailure());
			// Error was transformed to String type
			assertNotNull(result.join().fold(_ -> null, e -> e));
		}
	}

	// ==================== Transformation: mapBoth() ====================

	@Nested
	@DisplayName("AsyncResult.mapBoth()")
	class MapBothTests {

		@Test
		@DisplayName("Success: successMapper applied")
		void mapBoth_success() {
			final AsyncResult<Integer, TestError> original = AsyncResult.success(10);
			final AsyncResult<String, String> result = original.mapBoth(i -> "value-" + i, e -> "error-" + e);

			assertTrue(result.join().isSuccess());
			assertEquals("value-10", result.join().unwrap());
		}

		@Test
		@DisplayName("Failure: failureMapper applied")
		void mapBoth_failure() {
			final AsyncResult<Integer, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<String, String> result = original.mapBoth(i -> "value-" + i, e -> "error-" + e);

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Transformation: flatMap() ====================

	@Nested
	@DisplayName("AsyncResult.flatMap()")
	class FlatMapTests {

		@Test
		@DisplayName("Success: chains async operations")
		void flatMap_success() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<String, TestError> result = original
					.flatMap(u -> AsyncResult.success(u.name().toUpperCase()));

			assertTrue(result.join().isSuccess());
			assertEquals("JOHN", result.join().unwrap());
		}

		@Test
		@DisplayName("Failure: short-circuits")
		void flatMap_failure() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AtomicInteger mapperCalled = new AtomicInteger(0);
			final AsyncResult<String, TestError> result = original.flatMap(u -> {
				mapperCalled.incrementAndGet();
				return AsyncResult.success(u.name());
			});

			assertTrue(result.join().isFailure());
			assertEquals(0, mapperCalled.get());
		}

		@Test
		@DisplayName("Success: mapper returning Failure propagates")
		void flatMap_mapperReturnsFailure() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<String, TestError> result = original
					.flatMap(_ -> AsyncResult.failure(new TestError.Invalid("error")));

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Transformation: combine() ====================

	@Nested
	@DisplayName("AsyncResult.combine()")
	class CombineTests {

		@Test
		@DisplayName("both Completed Success: combines immediately")
		void combine_bothCompleted() {
			final AsyncResult<Integer, TestError> a = AsyncResult.success(10);
			final AsyncResult<Integer, TestError> b = AsyncResult.success(20);
			final AsyncResult<Integer, TestError> result = a.combine(b, Integer::sum);

			assertInstanceOf(AsyncResult.Completed.class, result);
			assertEquals(30, result.join().unwrap());
		}

		@Test
		@DisplayName("first Failure: returns first Failure")
		void combine_firstFailure() {
			final AsyncResult<Integer, TestError> a = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<Integer, TestError> b = AsyncResult.success(20);
			final AsyncResult<Integer, TestError> result = a.combine(b, Integer::sum);

			assertTrue(result.join().isFailure());
		}

		@Test
		@DisplayName("second Failure: returns second Failure")
		void combine_secondFailure() {
			final AsyncResult<Integer, TestError> a = AsyncResult.success(10);
			final AsyncResult<Integer, TestError> b = AsyncResult.failure(new TestError.NotFound("2"));
			final AsyncResult<Integer, TestError> result = a.combine(b, Integer::sum);

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Recovery: recover() ====================

	@Nested
	@DisplayName("AsyncResult.recover()")
	class RecoverTests {

		@Test
		@DisplayName("Success: returns unchanged")
		void recover_success() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<User, TestError> result = original.recover(_ -> new User("0", "Guest", 0));

			assertTrue(result.join().isSuccess());
			assertEquals("John", result.join().unwrap().name());
		}

		@Test
		@DisplayName("Failure: applies recovery")
		void recover_failure() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<User, TestError> result = original.recover(_ -> new User("0", "Guest", 0));

			assertTrue(result.join().isSuccess());
			assertEquals("Guest", result.join().unwrap().name());
		}
	}

	// ==================== Recovery: recoverWith() ====================

	@Nested
	@DisplayName("AsyncResult.recoverWith()")
	class RecoverWithTests {

		@Test
		@DisplayName("Success: returns unchanged")
		void recoverWith_success() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<User, TestError> result = original
					.recoverWith(_ -> AsyncResult.success(new User("0", "Guest", 0)));

			assertTrue(result.join().isSuccess());
			assertEquals("John", result.join().unwrap().name());
		}

		@Test
		@DisplayName("Failure: applies recovery")
		void recoverWith_failure() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<User, TestError> result = original
					.recoverWith(_ -> AsyncResult.success(new User("0", "Guest", 0)));

			assertTrue(result.join().isSuccess());
			assertEquals("Guest", result.join().unwrap().name());
		}

		@Test
		@DisplayName("Failure: recovery returning Failure propagates")
		void recoverWith_recoveryFailure() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<User, TestError> result = original
					.recoverWith(_ -> AsyncResult.failure(new TestError.Invalid("cannot recover")));

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Terminal: filter() ====================

	@Nested
	@DisplayName("AsyncResult.filter()")
	class FilterTests {

		@Test
		@DisplayName("Success + predicate true: unchanged")
		void filter_successPredicateTrue() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<User, TestError> result = original.filter(u -> u.age() >= 18,
					() -> new TestError.Invalid("underage"));

			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("Success + predicate false: returns Failure")
		void filter_successPredicateFalse() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 15));
			final AsyncResult<User, TestError> result = original.filter(u -> u.age() >= 18,
					() -> new TestError.Invalid("underage"));

			assertTrue(result.join().isFailure());
		}

		@Test
		@DisplayName("Failure: passes through")
		void filter_failure() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<User, TestError> result = original.filter(u -> u.age() >= 18,
					() -> new TestError.Invalid("underage"));

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Terminal: peek() ====================

	@Nested
	@DisplayName("AsyncResult.peek()")
	class PeekTests {

		@Test
		@DisplayName("Success: consumer executed")
		void peek_success() {
			final AtomicBoolean called = new AtomicBoolean(false);
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<User, TestError> result = original.peek(_ -> called.set(true));

			assertTrue(called.get());
			assertEquals(original.join(), result.join());
		}

		@Test
		@DisplayName("Failure: consumer not executed")
		void peek_failure() {
			final AtomicBoolean called = new AtomicBoolean(false);
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<User, TestError> result = original.peek(_ -> called.set(true));

			assertFalse(called.get());
			assertEquals(original.join(), result.join());
		}
	}

	// ==================== Terminal: peekFailure() ====================

	@Nested
	@DisplayName("AsyncResult.peekFailure()")
	class PeekFailureTests {

		@Test
		@DisplayName("Failure: consumer executed")
		void peekFailure_failure() {
			final AtomicBoolean called = new AtomicBoolean(false);

			AsyncResult.<String, String>failure("error").peekFailure(_ -> called.set(true));

			assertTrue(called.get());
		}

		@Test
		@DisplayName("Success: consumer not executed")
		void peekFailure_success() {
			final AtomicBoolean called = new AtomicBoolean(false);
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<User, TestError> result = original.peekFailure(_ -> called.set(true));

			assertFalse(called.get());
			assertEquals(original.join(), result.join());
		}
	}

	// ==================== Terminal: timeout() ====================

	@Nested
	@DisplayName("AsyncResult.timeout()")
	class TimeoutTests {

		@Test
		@DisplayName("Completed: returns unchanged")
		void timeout_completed() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<User, TestError> result = original.timeout(Duration.ofSeconds(1),
					() -> new TestError.Invalid("timeout"));

			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("Pending times out")
		@Timeout(value = 3, unit = SECONDS)
		void timeout_pendingTimesOut() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AsyncResult<User, TestError> result = original.timeout(Duration.ofMillis(100),
					() -> new TestError.Invalid("timeout"));

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Terminal: toFuture() ====================

	@Nested
	@DisplayName("AsyncResult.toFuture()")
	class ToFutureTests {

		@Test
		@DisplayName("Completed: returns completed future")
		void toFuture_completed() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final CompletableFuture<Result<User, TestError>> future = original.toFuture();

			assertTrue(future.isDone());
			assertTrue(future.join().isSuccess());
		}
	}

	// ==================== Terminal: isCompletedExceptionally()
	// ====================

	@Nested
	@DisplayName("AsyncResult.isCompletedExceptionally()")
	class IsCompletedExceptionallyTests {

		@Test
		@DisplayName("Completed Success: returns false")
		void isCompletedExceptionally_success() {
			final AsyncResult<User, TestError> result = AsyncResult.success(new User("1", "John", 30));
			assertFalse(result.isCompletedExceptionally());
		}

		@Test
		@DisplayName("Completed Failure: returns true")
		void isCompletedExceptionally_failure() {
			final AsyncResult<User, TestError> result = AsyncResult.failure(new TestError.NotFound("1"));
			assertTrue(result.isCompletedExceptionally());
		}
	}

	// ==================== Terminal: cancel() / isCancelled() ====================

	@Nested
	@DisplayName("AsyncResult.cancel() / isCancelled()")
	class CancelTests {

		@Test
		@DisplayName("Completed: cancel returns true")
		void cancel_completed() {
			final AsyncResult<User, TestError> result = AsyncResult.success(new User("1", "John", 30));
			assertTrue(result.cancel(true));
		}

		@Test
		@DisplayName("Completed: isCancelled returns false")
		void isCancelled_completed() {
			final AsyncResult<User, TestError> result = AsyncResult.success(new User("1", "John", 30));
			assertFalse(result.isCancelled());
		}

		@Test
		@DisplayName("Pending cancelled: isCancelled returns true")
		@Timeout(value = 2, unit = SECONDS)
		void isCancelled_pendingCancelled() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> result = AsyncResult.of(future);

			future.cancel(true);

			assertTrue(result.isCancelled());
		}
	}

	// ==================== Static Factory: retry() / retryWithBackoff()
	// ====================

	@Nested
	@DisplayName("AsyncResult.retry() / retryWithBackoff()")
	class RetryTests {

		@Test
		@DisplayName("retry with maxAttempts=1 returns immediately")
		void retry_singleAttempt() {
			final AtomicInteger attempts = new AtomicInteger(0);

			final AsyncResult<Integer, TestError> result = AsyncResult.retry(() -> {
				attempts.incrementAndGet();
				return AsyncResult.success(1);
			}, 1);

			assertEquals(1, attempts.get());
			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("retry retries on failure")
		@Timeout(value = 3, unit = SECONDS)
		void retry_retries() {
			final AtomicInteger attempts = new AtomicInteger(0);

			final AsyncResult<Integer, TestError> result = AsyncResult.retry(() -> {
				final int attempt = attempts.incrementAndGet();
				if (attempt < 3) {
					return AsyncResult.failure(new TestError.Invalid("fail"));
				}
				return AsyncResult.success(attempt);
			}, 3);

			assertTrue(result.join().isSuccess());
			assertEquals(3, result.join().unwrap());
		}

		@Test
		@DisplayName("retryWithBackoff with default multiplier")
		@Timeout(value = 5, unit = SECONDS)
		void retryWithBackoff_default() {
			final AtomicInteger attempts = new AtomicInteger(0);

			final AsyncResult<Integer, TestError> result = AsyncResult.retryWithBackoff(() -> {
				attempts.incrementAndGet();
				if (attempts.get() < 2) {
					return AsyncResult.failure(new TestError.Invalid("fail"));
				}
				return AsyncResult.success(attempts.get());
			}, 3, Duration.ofMillis(50));

			assertTrue(result.join().isSuccess());
		}
	}

	// ==================== Transformation: map() on Failure ====================

	@Nested
	@DisplayName("AsyncResult.map() on Failure")
	class MapOnFailureTests {

		@Test
		@DisplayName("Completed Failure: mapper not invoked")
		void map_failureNotInvoked() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AtomicInteger mapperCalled = new AtomicInteger(0);
			final AsyncResult<String, TestError> result = original.map(u -> {
				mapperCalled.incrementAndGet();
				return u.name();
			});

			assertTrue(result.join().isFailure());
			assertEquals(0, mapperCalled.get());
		}

		@Test
		@DisplayName("Pending Failure: mapper not invoked when completed")
		@Timeout(value = 2, unit = SECONDS)
		void map_pendingFailureNotInvoked() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AtomicInteger mapperCalled = new AtomicInteger(0);
			final AsyncResult<String, TestError> result = original.map(u -> {
				mapperCalled.incrementAndGet();
				return u.name();
			});

			future.complete(Result.failure(new TestError.NotFound("1")));

			assertTrue(result.join().isFailure());
			assertEquals(0, mapperCalled.get());
		}
	}

	// ==================== Transformation: mapError() branches ====================

	@Nested
	@DisplayName("AsyncResult.mapError() branches")
	class MapErrorBranchesTests {

		@Test
		@DisplayName("Success: error mapper not invoked")
		void mapError_successNotInvoked() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AtomicInteger mapperCalled = new AtomicInteger(0);
			final AsyncResult<User, String> result = original.mapError(e -> {
				mapperCalled.incrementAndGet();
				return e.toString();
			});

			assertTrue(result.join().isSuccess());
			assertEquals(0, mapperCalled.get());
		}

		@Test
		@DisplayName("Failure: error mapper invoked")
		void mapError_failureInvoked() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("123"));
			final AtomicInteger mapperCalled = new AtomicInteger(0);
			final AsyncResult<User, String> result = original.mapError(e -> {
				mapperCalled.incrementAndGet();
				return "error-" + e;
			});

			assertTrue(result.join().isFailure());
			assertEquals(1, mapperCalled.get());
		}

		@Test
		@DisplayName("Pending Success: mapError transforms on completion")
		@Timeout(value = 2, unit = SECONDS)
		void mapError_pendingSuccess() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AsyncResult<User, String> result = original.mapError(e -> "error-" + e);

			future.complete(Result.success(new User("1", "John", 30)));

			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("Pending Failure: mapError transforms error")
		@Timeout(value = 2, unit = SECONDS)
		void mapError_pendingFailure() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AsyncResult<User, String> result = original.mapError(e -> "error-" + e);

			future.complete(Result.failure(new TestError.NotFound("123")));

			assertTrue(result.join().isFailure());
		}
	}

	// ==================== Transformation: mapBoth() branches ====================

	@Nested
	@DisplayName("AsyncResult.mapBoth() branches")
	class MapBothBranchesTests {

		@Test
		@DisplayName("Failure: only failureMapper invoked")
		void mapBoth_failureInvoked() {
			final AsyncResult<Integer, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AtomicInteger successCalled = new AtomicInteger(0);
			final AtomicInteger failureCalled = new AtomicInteger(0);
			final AsyncResult<String, String> result = original.mapBoth(i -> {
				successCalled.incrementAndGet();
				return "value-" + i;
			}, e -> {
				failureCalled.incrementAndGet();
				return "error-" + e;
			});

			assertTrue(result.join().isFailure());
			assertEquals(0, successCalled.get());
			assertEquals(1, failureCalled.get());
		}
	}

	// ==================== Transformation: flatMap() branches ====================

	@Nested
	@DisplayName("AsyncResult.flatMap() branches")
	class FlatMapBranchesTests {

		@Test
		@DisplayName("Failure: mapper not invoked")
		void flatMap_failureNotInvoked() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AtomicInteger mapperCalled = new AtomicInteger(0);
			final AsyncResult<String, TestError> result = original.flatMap(u -> {
				mapperCalled.incrementAndGet();
				return AsyncResult.success(u.name());
			});

			assertTrue(result.join().isFailure());
			assertEquals(0, mapperCalled.get());
		}

		@Test
		@DisplayName("Success: mapper returning Failure propagates")
		void flatMap_mapperReturnsFailure() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AtomicInteger mapperCalled = new AtomicInteger(0);
			final AsyncResult<String, TestError> result = original.flatMap(_ -> {
				mapperCalled.incrementAndGet();
				return AsyncResult.failure(new TestError.Invalid("error"));
			});

			assertTrue(result.join().isFailure());
			assertEquals(1, mapperCalled.get());
		}

		@Test
		@DisplayName("Pending: flatMap chains when future completes")
		@Timeout(value = 2, unit = SECONDS)
		void flatMap_pendingSuccess() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AsyncResult<String, TestError> result = original
					.flatMap(u -> AsyncResult.success(u.name().toUpperCase()));

			future.complete(Result.success(new User("1", "John", 30)));

			assertTrue(result.join().isSuccess());
			assertEquals("JOHN", result.join().unwrap());
		}

		@Test
		@DisplayName("Pending: flatMap short-circuits on Failure")
		@Timeout(value = 2, unit = SECONDS)
		void flatMap_pendingFailure() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AtomicInteger mapperCalled = new AtomicInteger(0);
			final AsyncResult<String, TestError> result = original.flatMap(u -> {
				mapperCalled.incrementAndGet();
				return AsyncResult.success(u.name());
			});

			future.complete(Result.failure(new TestError.NotFound("1")));

			assertTrue(result.join().isFailure());
			assertEquals(0, mapperCalled.get());
		}
	}

	// ==================== Terminal: filter() branches ====================

	@Nested
	@DisplayName("AsyncResult.filter() branches")
	class FilterBranchesTests {

		@Test
		@DisplayName("Failure: passes through without filtering")
		void filter_failure() {
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			final AtomicInteger predicateCalled = new AtomicInteger(0);
			final AsyncResult<User, TestError> result = original.filter(u -> {
				predicateCalled.incrementAndGet();
				return u.age() >= 18;
			}, () -> new TestError.Invalid("underage"));

			assertTrue(result.join().isFailure());
			assertEquals(0, predicateCalled.get());
		}

		@Test
		@DisplayName("Success + predicate false: returns Failure with supplied error")
		void filter_predicateFalseReturnsFailure() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 15));
			final AtomicInteger predicateCalled = new AtomicInteger(0);
			final AsyncResult<User, TestError> result = original.filter(u -> {
				predicateCalled.incrementAndGet();
				return u.age() >= 18;
			}, () -> new TestError.Invalid("underage"));

			assertTrue(result.join().isFailure());
			assertEquals(1, predicateCalled.get());
		}
	}

	// ==================== Recovery: recover() branches ====================

	@Nested
	@DisplayName("AsyncResult.recover() branches")
	class RecoverBranchesTests {

		@Test
		@DisplayName("Success: recovery not invoked")
		void recover_successNotInvoked() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AtomicInteger recoverCalled = new AtomicInteger(0);
			final AsyncResult<User, TestError> result = original.recover(_ -> {
				recoverCalled.incrementAndGet();
				return new User("0", "Guest", 0);
			});

			assertTrue(result.join().isSuccess());
			assertEquals("John", result.join().unwrap().name());
			assertEquals(0, recoverCalled.get());
		}
	}

	// ==================== Recovery: recoverWith() branches ====================

	@Nested
	@DisplayName("AsyncResult.recoverWith() branches")
	class RecoverWithBranchesTests {

		@Test
		@DisplayName("Success: recovery not invoked")
		void recoverWith_successNotInvoked() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AtomicInteger recoverCalled = new AtomicInteger(0);
			final AsyncResult<User, TestError> result = original.recoverWith(_ -> {
				recoverCalled.incrementAndGet();
				return AsyncResult.success(new User("0", "Guest", 0));
			});

			assertTrue(result.join().isSuccess());
			assertEquals("John", result.join().unwrap().name());
			assertEquals(0, recoverCalled.get());
		}
	}

	// ==================== Terminal: peek() branches ====================

	@Nested
	@DisplayName("AsyncResult.peek() branches")
	class PeekBranchesTests {

		@Test
		@DisplayName("Failure: consumer not executed")
		void peek_failure() {
			final AtomicBoolean called = new AtomicBoolean(false);
			final AsyncResult<User, TestError> original = AsyncResult.failure(new TestError.NotFound("1"));
			original.peek(_ -> called.set(true));

			assertFalse(called.get());
		}
	}

	// ==================== Terminal: peekFailure() branches ====================

	@Nested
	@DisplayName("AsyncResult.peekFailure() branches")
	class PeekFailureBranchesTests {

		@Test
		@DisplayName("Success: consumer not executed")
		void peekFailure_success() {
			final AtomicBoolean called = new AtomicBoolean(false);
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			original.peekFailure(_ -> called.set(true));

			assertFalse(called.get());
		}

		@Test
		@DisplayName("Pending Success: peekFailure not executed")
		@Timeout(value = 2, unit = SECONDS)
		void peekFailure_pendingSuccess() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AtomicBoolean called = new AtomicBoolean(false);
			original.peekFailure(_ -> called.set(true));

			future.complete(Result.success(new User("1", "John", 30)));

			assertFalse(called.get());
		}

		@Test
		@DisplayName("Pending Failure: peekFailure executed")
		@Timeout(value = 2, unit = SECONDS)
		void peekFailure_pendingFailure() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AtomicBoolean called = new AtomicBoolean(false);
			original.peekFailure(_ -> called.set(true));

			future.complete(Result.failure(new TestError.NotFound("1")));

			assertTrue(called.get());
		}
	}

	// ==================== Terminal: combine() branches ====================

	@Nested
	@DisplayName("AsyncResult.combine() branches")
	class CombineBranchesTests {

		@Test
		@DisplayName("first Failure: returns first Failure without combining")
		void combine_firstFailure() {
			final AsyncResult<Integer, TestError> a = AsyncResult.failure(new TestError.NotFound("1"));
			final AsyncResult<Integer, TestError> b = AsyncResult.success(20);
			final AtomicInteger combinerCalled = new AtomicInteger(0);
			final AsyncResult<Integer, TestError> result = a.combine(b, (x, y) -> {
				combinerCalled.incrementAndGet();
				return x + y;
			});

			assertTrue(result.join().isFailure());
			assertEquals(0, combinerCalled.get());
		}

		@Test
		@DisplayName("second Failure: returns second Failure")
		void combine_secondFailure() {
			final AsyncResult<Integer, TestError> a = AsyncResult.success(10);
			final AsyncResult<Integer, TestError> b = AsyncResult.failure(new TestError.NotFound("2"));
			final AsyncResult<Integer, TestError> result = a.combine(b, Integer::sum);

			assertTrue(result.join().isFailure());
		}

		@Test
		@DisplayName("Pending + Pending: combines when both complete")
		@Timeout(value = 2, unit = SECONDS)
		void combine_pendingPending() {
			final CompletableFuture<Result<Integer, TestError>> f1 = new CompletableFuture<>();
			final CompletableFuture<Result<Integer, TestError>> f2 = new CompletableFuture<>();
			final AsyncResult<Integer, TestError> a = AsyncResult.of(f1);
			final AsyncResult<Integer, TestError> b = AsyncResult.of(f2);
			final AsyncResult<Integer, TestError> result = a.combine(b, Integer::sum);

			f1.complete(Result.success(10));
			f2.complete(Result.success(20));

			assertTrue(result.join().isSuccess());
			assertEquals(30, result.join().unwrap());
		}
	}

	// ==================== Terminal: delay() branches ====================

	@Nested
	@DisplayName("AsyncResult.delay() branches")
	class DelayBranchesTests {

		@Test
		@DisplayName("Pending: delay applied when future completes")
		@Timeout(value = 3, unit = SECONDS)
		void delay_pending() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> original = AsyncResult.of(future);
			final AsyncResult<User, TestError> result = AsyncResult.delay(original, Duration.ofMillis(50));

			future.complete(Result.success(new User("1", "John", 30)));

			assertTrue(result.join().isSuccess());
		}
	}

	// ==================== Terminal: cancel() branches ====================

	@Nested
	@DisplayName("AsyncResult.cancel() branches")
	class CancelBranchesTests {

		@Test
		@DisplayName("Pending: cancel returns true")
		void cancel_pending() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> result = AsyncResult.of(future);

			assertTrue(result.cancel(true));
		}
	}

	// ==================== Terminal: isCompletedExceptionally() branches
	// ====================

	@Nested
	@DisplayName("AsyncResult.isCompletedExceptionally() branches")
	class IsCompletedExceptionallyBranchesTests {

		@Test
		@DisplayName("Pending: returns false")
		@Timeout(value = 2, unit = SECONDS)
		void isCompletedExceptionally_pending() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> result = AsyncResult.of(future);

			assertFalse(result.isCompletedExceptionally());

			future.complete(Result.success(new User("1", "John", 30)));
		}
	}

	// ==================== Terminal: isDone() branches ====================

	@Nested
	@DisplayName("AsyncResult.isDone() branches")
	class IsDoneBranchesTests {

		@Test
		@DisplayName("Pending: returns false")
		@Timeout(value = 2, unit = SECONDS)
		void isDone_pending() {
			final CompletableFuture<Result<User, TestError>> future = new CompletableFuture<>();
			final AsyncResult<User, TestError> result = AsyncResult.of(future);

			assertFalse(result.isDone());

			future.complete(Result.success(new User("1", "John", 30)));
		}
	}

	// ==================== Edge Cases ====================

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCaseTests {

		@Test
		@DisplayName("chaining multiple transformations")
		void chaining() {
			final var result = AsyncResult.success(new User("1", "John", 30)).map(User::name).map(String::toUpperCase)
					.map(s -> "User: " + s);

			assertTrue(result.join().isSuccess());
			assertEquals("User: JOHN", result.join().unwrap());
		}

		@Test
		@DisplayName("transformations do not mutate original")
		void immutability() {
			final AsyncResult<User, TestError> original = AsyncResult.success(new User("1", "John", 30));
			final AsyncResult<String, TestError> mapped = original.map(User::name);

			final User originalUser = original.join().unwrap();
			final String mappedName = mapped.join().unwrap();

			assertEquals("John", mappedName);
			assertEquals("John", originalUser.name());
			assertTrue(original.join().isSuccess());
		}

		@Test
		@DisplayName("collectAll with custom Collector")
		void collectWithCollector() {
			final List<AsyncResult<Integer, TestError>> list = List.of(AsyncResult.success(1), AsyncResult.success(2),
					AsyncResult.success(3));

			final var result = AsyncResult.collectAll(list, Collectors.toSet());

			assertTrue(result.join().isSuccess());
			assertEquals(Set.of(1, 2, 3), result.join().unwrap());
		}

		@Test
		@DisplayName("collectAll with Pending results uses parallel collection")
		@Timeout(value = 3, unit = SECONDS)
		void collectAll_withPending() {
			final CompletableFuture<Result<Integer, TestError>> f1 = new CompletableFuture<>();
			final CompletableFuture<Result<Integer, TestError>> f2 = new CompletableFuture<>();
			final List<AsyncResult<Integer, TestError>> list = List.of(AsyncResult.of(f1), AsyncResult.of(f2));

			final var result = AsyncResult.collectAll(list);

			f1.complete(Result.success(1));
			f2.complete(Result.success(2));

			assertTrue(result.join().isSuccess());
			assertEquals(List.of(1, 2), result.join().unwrap());
		}

		@Test
		@DisplayName("collectAll with custom collector on Pending results")
		@Timeout(value = 3, unit = SECONDS)
		void collectAll_customCollectorWithPending() {
			final CompletableFuture<Result<Integer, TestError>> f1 = new CompletableFuture<>();
			final CompletableFuture<Result<Integer, TestError>> f2 = new CompletableFuture<>();
			final List<AsyncResult<Integer, TestError>> list = List.of(AsyncResult.of(f1), AsyncResult.of(f2));

			final var result = AsyncResult.collectAll(list, Collectors.toSet());

			f1.complete(Result.success(1));
			f2.complete(Result.success(2));

			assertTrue(result.join().isSuccess());
			assertEquals(Set.of(1, 2), result.join().unwrap());
		}

		@Test
		@DisplayName("sequence with custom collector")
		void sequence_customCollector() {
			final List<AsyncResult<Integer, TestError>> list = List.of(AsyncResult.success(1), AsyncResult.success(2));

			final var result = AsyncResult.sequence(list, Collectors.summingInt(Integer::intValue));

			assertTrue(result.join().isSuccess());
			assertEquals(3, result.join().unwrap());
		}

		@Test
		@DisplayName("retryWithBackoff with maxAttempts=1 returns immediately")
		void retryWithBackoff_singleAttempt() {
			final AtomicInteger attempts = new AtomicInteger(0);

			final AsyncResult<Integer, TestError> result = AsyncResult.retryWithBackoff(() -> {
				attempts.incrementAndGet();
				return AsyncResult.success(1);
			}, 1, Duration.ofMillis(10));

			assertEquals(1, attempts.get());
			assertTrue(result.join().isSuccess());
		}

		@Test
		@DisplayName("retryWithBackoff with Pending that fails triggers retry")
		@Timeout(value = 5, unit = SECONDS)
		void retryWithBackoff_pendingFailureTriggersRetry() {
			final AtomicInteger attemptCount = new AtomicInteger(0);

			final AsyncResult<Integer, TestError> result = AsyncResult.retryWithBackoff(() -> {
				final int attempt = attemptCount.incrementAndGet();
				if (attempt == 1) {
					final CompletableFuture<Result<Integer, TestError>> future = new CompletableFuture<>();
					final AsyncResult<Integer, TestError> pending = AsyncResult.of(future);
					future.complete(Result.failure(new TestError.Invalid("attempt-" + attempt)));
					return pending;
				} else {
					return AsyncResult.success(attempt * 10);
				}
			}, 3, Duration.ofMillis(10));

			assertTrue(result.join().isSuccess());
			assertEquals(20, result.join().unwrap());
		}

		@Test
		@DisplayName("retryWithBackoff with Pending that succeeds immediately")
		@Timeout(value = 3, unit = SECONDS)
		void retryWithBackoff_pendingSuccessNoRetry() {
			final AtomicInteger attemptCount = new AtomicInteger(0);

			final AsyncResult<Integer, TestError> result = AsyncResult.retryWithBackoff(() -> {
				attemptCount.incrementAndGet();
				final CompletableFuture<Result<Integer, TestError>> future = new CompletableFuture<>();
				final AsyncResult<Integer, TestError> pending = AsyncResult.of(future);
				future.complete(Result.success(42));
				return pending;
			}, 3, Duration.ofMillis(10));

			assertTrue(result.join().isSuccess());
			assertEquals(42, result.join().unwrap());
			assertEquals(1, attemptCount.get());
		}

		@Test
		@DisplayName("retry with Pending that fails triggers retry")
		@Timeout(value = 5, unit = SECONDS)
		void retry_pendingFailureTriggersRetry() {
			final AtomicInteger attemptCount = new AtomicInteger(0);

			final AsyncResult<Integer, TestError> result = AsyncResult.retry(() -> {
				final int attempt = attemptCount.incrementAndGet();
				if (attempt == 1) {
					final CompletableFuture<Result<Integer, TestError>> future = new CompletableFuture<>();
					final AsyncResult<Integer, TestError> pending = AsyncResult.of(future);
					future.complete(Result.failure(new TestError.Invalid("attempt-" + attempt)));
					return pending;
				} else {
					return AsyncResult.success(attempt * 10);
				}
			}, 3);

			assertTrue(result.join().isSuccess());
			assertEquals(20, result.join().unwrap());
		}

		@Test
		@DisplayName("retry with Pending that succeeds immediately")
		@Timeout(value = 3, unit = SECONDS)
		void retry_pendingSuccessNoRetry() {
			final AtomicInteger attemptCount = new AtomicInteger(0);

			final AsyncResult<Integer, TestError> result = AsyncResult.retry(() -> {
				attemptCount.incrementAndGet();
				final CompletableFuture<Result<Integer, TestError>> future = new CompletableFuture<>();
				final AsyncResult<Integer, TestError> pending = AsyncResult.of(future);
				future.complete(Result.success(42));
				return pending;
			}, 3);

			assertTrue(result.join().isSuccess());
			assertEquals(42, result.join().unwrap());
			assertEquals(1, attemptCount.get());
		}

		@Test
		@DisplayName("collectAll with Pending results - first future fails")
		@Timeout(value = 3, unit = SECONDS)
		void collectAll_pendingFirstFails() {
			final CompletableFuture<Result<Integer, TestError>> f1 = new CompletableFuture<>();
			final CompletableFuture<Result<Integer, TestError>> f2 = new CompletableFuture<>();
			final List<AsyncResult<Integer, TestError>> list = List.of(AsyncResult.of(f1), AsyncResult.of(f2));

			final var result = AsyncResult.collectAll(list);

			f1.complete(Result.failure(new TestError.Invalid("first-error")));
			f2.complete(Result.success(999));

			assertTrue(result.join().isFailure());
		}
	}
}
