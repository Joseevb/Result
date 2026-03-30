package dev.jose.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorRouterTest {

	// ==================== Test Helpers ====================

	interface TestError {
		String getMessage();
	}

	record InvalidError(String field, String reason) implements TestError {
		@Override
		public String getMessage() {
			return "Invalid: " + this.field + " - " + this.reason;
		}
	}

	record UnknownError(String message) implements TestError {
		@Override
		public String getMessage() {
			return this.message;
		}
	}

	// ==================== Static Factory: defaultsTo() ====================

	@Nested
	@DisplayName("ErrorRouter.defaultsTo()")
	class DefaultsToTests {

		@Test
		@DisplayName("creates router with fallback")
		void defaultsTo_basic() {
			final ErrorRouter<TestError> router = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));

			assertNotNull(router);
			assertEquals(0, router.ruleCount());
		}

		@Test
		@DisplayName("fallback is used when no rules match")
		void defaultsTo_fallbackUsed() {
			final ErrorRouter<TestError> router = ErrorRouter.defaultsTo(e -> new UnknownError(e.getMessage()));

			final TestError error = router.apply(new RuntimeException("test"));

			assertInstanceOf(UnknownError.class, error);
			assertEquals("test", ((UnknownError) error).message());
		}
	}

	// ==================== Mapping: map() ====================

	@Nested
	@DisplayName("ErrorRouter.map()")
	class MapTests {

		@Test
		@DisplayName("registers exception mapping")
		void map_basic() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(IllegalArgumentException.class, e -> new InvalidError("arg", e.getMessage()));

			assertEquals(1, router.ruleCount());
		}

		@Test
		@DisplayName("maps specific exception")
		void map_specific() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(IllegalArgumentException.class, e -> new InvalidError("arg", e.getMessage()));

			final TestError error = router.apply(new IllegalArgumentException("bad input"));

			assertInstanceOf(InvalidError.class, error);
			assertEquals("arg", ((InvalidError) error).field());
			assertEquals("bad input", ((InvalidError) error).reason());
		}

		@Test
		@DisplayName("maps subclasses")
		void map_subclasses() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(RuntimeException.class, e -> new UnknownError(e.getMessage()));

			final TestError error = router.apply(new IllegalArgumentException("test"));

			assertInstanceOf(UnknownError.class, error);
		}

		@Test
		@DisplayName("first matching rule wins")
		void map_order() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(IllegalArgumentException.class, _ -> new InvalidError("arg", "specific"))
					.map(RuntimeException.class, _ -> new UnknownError("fallback"));

			final TestError error = router.apply(new IllegalArgumentException("test"));

			assertInstanceOf(InvalidError.class, error);
		}

		@Test
		@DisplayName("is chainable")
		void map_chainable() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(IllegalArgumentException.class, _ -> new InvalidError("arg", ""))
					.map(NullPointerException.class, _ -> new InvalidError("npe", ""));

			assertEquals(2, router.ruleCount());
		}
	}

	// ==================== Terminal: apply() ====================

	@Nested
	@DisplayName("ErrorRouter.apply()")
	class ApplyTests {

		@Test
		@DisplayName("Function interface works")
		void apply_functionInterface() {
			final Function<Exception, TestError> function = ErrorRouter.defaultsTo(_ -> new UnknownError(""));

			final TestError error = function.apply(new RuntimeException("test"));

			assertInstanceOf(UnknownError.class, error);
		}

		@Test
		@DisplayName("maps UnknownException to fallback")
		void apply_unknownException() {
			final ErrorRouter<TestError> router = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IllegalArgumentException.class, _ -> new InvalidError("arg", ""));

			final TestError error = router.apply(new RuntimeException("unknown"));

			assertInstanceOf(UnknownError.class, error);
			assertEquals("unknown", error.getMessage());
		}
	}

	// ==================== Terminal: ruleCount() ====================

	@Nested
	@DisplayName("ErrorRouter.ruleCount()")
	class RuleCountTests {

		@Test
		@DisplayName("returns zero for empty router")
		void ruleCount_empty() {
			final ErrorRouter<TestError> router = ErrorRouter.defaultsTo(_ -> new UnknownError(""));

			assertEquals(0, router.ruleCount());
		}

		@Test
		@DisplayName("returns count of registered mappings")
		void ruleCount_withMappings() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(IllegalArgumentException.class, _ -> new InvalidError("arg", ""))
					.map(NullPointerException.class, _ -> new InvalidError("npe", ""));

			assertEquals(2, router.ruleCount());
		}
	}

	// ==================== Terminal: hasRuleFor() ====================

	@Nested
	@DisplayName("ErrorRouter.hasRuleFor()")
	class HasRuleForTests {

		@Test
		@DisplayName("returns true for registered type")
		void hasRuleFor_registered() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(IllegalArgumentException.class, _ -> new InvalidError("arg", ""));

			assertTrue(router.hasRuleFor(IllegalArgumentException.class));
		}

		@Test
		@DisplayName("returns false for unregistered type")
		void hasRuleFor_unregistered() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(IllegalArgumentException.class, _ -> new InvalidError("arg", ""));

			assertFalse(router.hasRuleFor(NullPointerException.class));
		}

		@Test
		@DisplayName("returns false for subclass of registered base")
		void hasRuleFor_subclass() {
			final ErrorRouter<TestError> router = ErrorRouter.<TestError>defaultsTo(_ -> new UnknownError(""))
					.map(RuntimeException.class, _ -> new UnknownError(""));

			assertFalse(router.hasRuleFor(IllegalArgumentException.class));
		}
	}

	// ==================== Edge Cases ====================

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCaseTests {

		@Test
		@DisplayName("chaining multiple mappings")
		void chaining() {
			final ErrorRouter<TestError> router = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IllegalArgumentException.class, e -> new InvalidError("arg", e.getMessage()))
					.map(NullPointerException.class, e -> new InvalidError("npe", e.getMessage()));

			assertEquals(2, router.ruleCount());

			final TestError error = router.apply(new IllegalArgumentException("test"));
			assertInstanceOf(InvalidError.class, error);
		}

		@Test
		@DisplayName("complex exception hierarchy")
		void complexHierarchy() {
			final ErrorRouter<TestError> router = ErrorRouter
					.<TestError>defaultsTo(e -> new UnknownError(e.getMessage()))
					.map(IllegalArgumentException.class, e -> new InvalidError("arg", e.getMessage()))
					.map(NullPointerException.class, e -> new InvalidError("npe", e.getMessage()));

			final TestError error1 = router.apply(new IllegalArgumentException("bad"));
			assertInstanceOf(InvalidError.class, error1);

			final TestError error2 = router.apply(new NullPointerException("null"));
			assertInstanceOf(InvalidError.class, error2);

			final TestError error3 = router.apply(new RuntimeException("runtime"));
			assertInstanceOf(UnknownError.class, error3);
		}

		@Test
		@DisplayName("immutability: original router unchanged after map")
		void immutability() {
			final ErrorRouter<TestError> original = ErrorRouter.defaultsTo(_ -> new UnknownError(""));
			final ErrorRouter<TestError> modified = original.map(IllegalArgumentException.class,
					_ -> new InvalidError("arg", ""));

			assertEquals(0, original.ruleCount());
			assertEquals(1, modified.ruleCount());
		}
	}
}
