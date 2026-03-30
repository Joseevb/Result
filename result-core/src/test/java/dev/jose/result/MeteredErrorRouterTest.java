package dev.jose.result;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeteredErrorRouterTest {

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

	// ==================== Constructor ====================

	@Nested
	@DisplayName("Constructor")
	@SuppressWarnings("DataFlowIssue")
	class ConstructorTests {

		@Test
		@DisplayName("throws NullPointerException when delegate is null")
		void throwsOnNullDelegate() {
			final MeterRegistry registry = new SimpleMeterRegistry();

			assertThrows(NullPointerException.class, () -> new MeteredErrorRouter<>(null, registry, "test.metric"));
		}

		@Test
		@DisplayName("throws NullPointerException when meterRegistry is null")
		void throwsOnNullMeterRegistry() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));

			assertThrows(NullPointerException.class, () -> new MeteredErrorRouter<>(delegate, null, "test.metric"));
		}

		@Test
		@DisplayName("throws NullPointerException when metricName is null")
		void throwsOnNullMetricName() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();

			assertThrows(NullPointerException.class, () -> new MeteredErrorRouter<>(delegate, registry, null));
		}

		@Test
		@DisplayName("constructor with commonTags works")
		void constructorWithCommonTags() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();
			final Iterable<Tag> tags = Tags.of("environment", "test");

			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "test.metric",
					tags);

			assertNotNull(router);
		}

		@Test
		@DisplayName("throws NullPointerException when commonTags is null")
		void throwsOnNullCommonTags() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();

			assertThrows(NullPointerException.class,
					() -> new MeteredErrorRouter<>(delegate, registry, "test.metric", null));
		}
	}

	// ==================== apply() ====================

	@Nested
	@DisplayName("apply()")
	class ApplyTests {

		@Test
		@DisplayName("delegates and returns result correctly")
		void delegatesAndReturnsResult() {
			final ErrorRouter<TestError> delegate = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IOException.class, e -> new IoError(e.getMessage()));

			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors");

			final TestError result = router.apply(new IOException("test io"));

			assertNotNull(result);
		}

		@Test
		@DisplayName("throws NullPointerException when exception is null")
		@SuppressWarnings("DataFlowIssue")
		void throwsOnNullException() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors");

			assertThrows(NullPointerException.class, () -> router.apply(null));
		}
	}

	// ==================== Metrics ====================

	@Nested
	@DisplayName("Metrics")
	class MetricsTests {

		@Test
		@DisplayName("counter incremented on each call")
		void counterIncrementedOnEachCall() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors");

			router.apply(new RuntimeException("1"));
			router.apply(new RuntimeException("2"));
			router.apply(new RuntimeException("3"));

			final Counter counter = registry.find("errors").counter();
			assertNotNull(counter);
			assertEquals(3.0, counter.count(), 0.001);
		}

		@Test
		@DisplayName("exception tag records exception class name")
		void exceptionTagRecordsClassName() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors");

			router.apply(new IOException("test"));

			final Counter counter = registry.find("errors").tags("exception", "IOException").counter();
			assertNotNull(counter);
			assertEquals(1.0, counter.count(), 0.001);
		}

		@Test
		@DisplayName("mapped_to tag records error class name")
		void mappedToTagRecordsErrorClassName() {
			final ErrorRouter<TestError> delegate = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IOException.class, e -> new IoError(e.getMessage()));

			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors");

			router.apply(new IOException("test"));

			final Counter counter = registry.find("errors").tags("mapped_to", "IoError").counter();
			assertNotNull(counter);
			assertEquals(1.0, counter.count(), 0.001);
		}

		@Test
		@DisplayName("commonTags applied to counter")
		void commonTagsApplied() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();
			final Iterable<Tag> commonTags = Tags.of("service", "test-service", "env", "test");
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors",
					commonTags);

			router.apply(new RuntimeException("test"));

			final Counter counter = registry.find("errors").tags("service", "test-service", "env", "test").counter();

			assertNotNull(counter);
			assertEquals(1.0, counter.count(), 0.001);
		}

		@Test
		@DisplayName("uses custom metric name")
		void usesCustomMetricName() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry,
					"custom.metric.name");

			router.apply(new RuntimeException("test"));

			final Counter counter = registry.find("custom.metric.name").counter();
			assertNotNull(counter);
			assertEquals(1.0, counter.count(), 0.001);
		}

		@Test
		@DisplayName("description is set on counter")
		void descriptionIsSet() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors");

			router.apply(new RuntimeException("test"));

			final Counter counter = registry.counter("errors", "exception", "RuntimeException", "mapped_to",
					"UnknownError");
			assertNotNull(counter);
			assertEquals(1.0, counter.count(), 0.001);
		}
	}

	// ==================== delegate() ====================

	@Nested
	@DisplayName("delegate()")
	class DelegateTests {

		@Test
		@DisplayName("returns underlying ErrorRouter")
		void returnsUnderlyingRouter() {
			final ErrorRouter<TestError> delegate = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));
			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors");

			assertEquals(delegate, router.delegate());
		}

		@Test
		@DisplayName("allows accessing ruleCount on delegate")
		void allowsAccessingRuleCount() {
			final ErrorRouter<TestError> delegate = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IOException.class, e -> new IoError(e.getMessage()));

			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> router = new MeteredErrorRouter<>(delegate, registry, "errors");

			assertEquals(1, router.delegate().ruleCount());
		}
	}

	// ==================== Composition ====================

	@Nested
	@DisplayName("Composition")
	class CompositionTests {

		@Test
		@DisplayName("MeteredErrorRouter can wrap core ErrorRouter")
		void meteredWrapsCoreRouter() {
			final ErrorRouter<TestError> coreRouter = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IOException.class, e -> new IoError(e.getMessage()));

			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> meteredRouter = new MeteredErrorRouter<>(coreRouter, registry,
					"errors");

			final TestError result = meteredRouter.apply(new IOException("test"));

			assertNotNull(result);

			final Counter counter = registry.counter("errors", "exception", "IOException", "mapped_to", "IoError");
			assertEquals(1.0, counter.count(), 0.001);
		}

		@Test
		@DisplayName("CachedErrorRouter can wrap MeteredErrorRouter's delegate")
		void cachedCanWrapMeteredDelegate() {
			final ErrorRouter<TestError> coreRouter = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));

			final MeterRegistry registry = new SimpleMeterRegistry();
			final MeteredErrorRouter<TestError> meteredRouter = new MeteredErrorRouter<>(coreRouter, registry,
					"errors");

			// MeteredErrorRouter wraps ErrorRouter, so we can pass meteredRouter.delegate()
			// to CachedErrorRouter
			final CachedErrorRouter<TestError> cachedRouter = new CachedErrorRouter<>(meteredRouter.delegate());

			cachedRouter.apply(new RuntimeException("1"));
			cachedRouter.apply(new IllegalStateException("2"));
			cachedRouter.apply(new IllegalArgumentException("3"));

			// CachedErrorRouter doesn't use MeteredErrorRouter, so counter won't be
			// incremented
			assertEquals(3.0, cachedRouter.cacheSize());
		}
	}
}
