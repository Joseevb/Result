package dev.jose.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CachedErrorRouterTest {

	interface TestError {
		String getMessage();
	}

	record UnknownError(String message) implements TestError {
		@Override
		public String getMessage() {
			return this.message;
		}
	}

	record IoError(String msg) implements TestError {
		@Override
		public String getMessage() {
			return this.msg;
		}
	}

	record DbError(String msg) implements TestError {
		@Override
		public String getMessage() {
			return this.msg;
		}
	}

	// ==================== Constructor ====================

	@Nested
	@DisplayName("Constructor")
	@SuppressWarnings("DataFlowIssue")
	class ConstructorTests {

		@Test
		@DisplayName("throws NullPointerException when delegate is null")
		void throwsOnNullDelegate() {
			assertThrows(NullPointerException.class, () -> new CachedErrorRouter<>(null));
		}
	}

	// ==================== apply() ====================

	@Nested
	@DisplayName("apply()")
	class ApplyTests {

		@Test
		@DisplayName("first call delegates and caches result")
		void firstCallDelegatesAndCaches() {
			final AtomicInteger delegateCalls = new AtomicInteger(0);
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.defaultsTo(e -> {
				delegateCalls.incrementAndGet();
				return new UnknownError(e.getMessage());
			});

			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			final TestError result = router.apply(new RuntimeException("test"));

			assertEquals(1, delegateCalls.get());
			assertNotNull(result);
		}

		@Test
		@DisplayName("subsequent calls return cached result without delegating")
		void subsequentCallsReturnCached() {
			final AtomicInteger delegateCalls = new AtomicInteger(0);
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.defaultsTo(e -> {
				delegateCalls.incrementAndGet();
				return new UnknownError(e.getMessage());
			});

			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			router.apply(new RuntimeException("test"));
			router.apply(new RuntimeException("test"));
			router.apply(new RuntimeException("test"));

			assertEquals(1, delegateCalls.get());
		}

		@Test
		@DisplayName("different exception classes have separate cache entries")
		void differentExceptionClassesSeparateCache() {
			final AtomicInteger delegateCalls = new AtomicInteger(0);
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.<TestError>defaultsTo(e -> {
				delegateCalls.incrementAndGet();
				return new UnknownError(e.getMessage());
			}).map(IOException.class, e -> {
				delegateCalls.incrementAndGet();
				return new IoError(e.getMessage());
			}).map(SQLException.class, e -> {
				delegateCalls.incrementAndGet();
				return new DbError(e.getMessage());
			});

			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			router.apply(new IOException("io"));
			router.apply(new SQLException("sql"));
			router.apply(new IOException("io2"));
			router.apply(new SQLException("sql2"));

			assertEquals(2, delegateCalls.get()); // Now this will pass
		}

		@Test
		@DisplayName("throws NullPointerException when exception is null")
		@SuppressWarnings("DataFlowIssue")
		void throwsOnNullException() {
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			assertThrows(NullPointerException.class, () -> router.apply(null));
		}

		@Test
		@DisplayName("caches null results correctly")
		void cachesNullResults() {
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.defaultsTo(e -> null);
			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			final TestError result1 = router.apply(new RuntimeException("test"));
			final TestError result2 = router.apply(new RuntimeException("test"));

			assertNull(result1);
			assertNull(result2);
			assertEquals(1, router.cacheSize());
		}
	}

	// ==================== cacheSize() ====================

	@Nested
	@DisplayName("cacheSize()")
	class CacheSizeTests {

		@Test
		@DisplayName("returns 0 for empty cache")
		void returnsZeroForEmpty() {
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			assertEquals(0, router.cacheSize());
		}

		@Test
		@DisplayName("returns correct count after unique exceptions")
		void returnsCorrectCount() {
			final ErrorRouter<TestError> delegateRouter = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IOException.class, e -> new IoError(e.getMessage()))
					.map(SQLException.class, e -> new DbError(e.getMessage()));

			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			router.apply(new IOException("1"));
			router.apply(new IOException("2"));
			router.apply(new SQLException("3"));

			assertEquals(2, router.cacheSize());
		}
	}

	// ==================== clearCache() ====================

	@Nested
	@DisplayName("clearCache()")
	class ClearCacheTests {

		@Test
		@DisplayName("empties cache after clearing")
		void emptiesCacheAfterClear() {
			final AtomicInteger delegateCalls = new AtomicInteger(0);
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.defaultsTo(e -> {
				delegateCalls.incrementAndGet();
				return new UnknownError(e.getMessage());
			});

			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			router.apply(new RuntimeException("test"));
			assertEquals(1, router.cacheSize());

			router.clearCache();
			assertEquals(0, router.cacheSize());

			router.apply(new RuntimeException("test"));
			assertEquals(2, delegateCalls.get());
		}

		@Test
		@DisplayName("can be called on empty cache without error")
		void callOnEmptyCacheIsNoOp() {
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			router.clearCache();
			assertEquals(0, router.cacheSize());
		}
	}

	// ==================== delegate() ====================

	@Nested
	@DisplayName("delegate()")
	class DelegateTests {

		@Test
		@DisplayName("returns underlying ErrorRouter")
		void returnsUnderlyingRouter() {
			final ErrorRouter<TestError> delegateRouter = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			assertEquals(delegateRouter, router.delegate());
		}

		@Test
		@DisplayName("allows accessing ruleCount on delegate")
		void allowsAccessingRuleCount() {
			final ErrorRouter<TestError> delegateRouter = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IOException.class, e -> new IoError(e.getMessage()));

			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			assertEquals(1, router.delegate().ruleCount());
		}
	}

	// ==================== Thread Safety ====================

	@Nested
	@DisplayName("Thread Safety")
	class ThreadSafetyTests {

		@Test
		@DisplayName("handles concurrent access without data loss")
		void concurrentAccessIsSafe() throws InterruptedException {
			final ErrorRouter<TestError> delegateRouter = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IOException.class, e -> new IoError(e.getMessage()));

			final CachedErrorRouter<TestError> router = new CachedErrorRouter<>(delegateRouter);

			final Thread t1 = new Thread(() -> {
				for (int i = 0; i < 100; i++) {
					router.apply(new IOException("io" + i));
				}
			});

			final Thread t2 = new Thread(() -> {
				for (int i = 0; i < 100; i++) {
					router.apply(new SQLException("sql" + i));
				}
			});

			t1.start();
			t2.start();
			t1.join();
			t2.join();

			assertEquals(2, router.cacheSize());
		}
	}
}
