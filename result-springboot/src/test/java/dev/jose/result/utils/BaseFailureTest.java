package dev.jose.result.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseFailureTest {

	record NotFoundUser(String id) implements BaseFailure {
		@Override
		public String getMessage() {
			return "User not found";
		}
	}

	record SimpleError() implements BaseFailure {
		@Override
		public String getMessage() {
			return "An error occurred";
		}
	}

	record IORetryError(String operation) implements BaseFailure {
		@Override
		public String getMessage() {
			return "IO error during " + this.operation;
		}
	}

	record ParameterizedError(String id) implements BaseFailure {
		@Override
		public String getMessage() {
			return "Resource not found";
		}

		@Override
		public Object[] getMessageArgs() {
			return new Object[]{this.id};
		}
	}

	record CustomExtensionsError() implements BaseFailure {
		@Override
		public String getMessage() {
			return "Custom error";
		}

		@Override
		public Map<String, Object> getExtensions() {
			return Map.of("retryCount", 3, "severity", "high");
		}
	}

	record NonLoggingError() implements BaseFailure {
		@Override
		public String getMessage() {
			return "Validation error";
		}

		@Override
		public boolean shouldLog() {
			return false;
		}
	}

	record InfoLevelError() implements BaseFailure {
		@Override
		public String getMessage() {
			return "Info level error";
		}

		@Override
		public String getLogLevel() {
			return "INFO";
		}
	}

	record CustomTitleError() implements BaseFailure {
		@Override
		public String getMessage() {
			return "Custom title error";
		}

		@Override
		public String getTitle() {
			return "Custom Title";
		}
	}

	record LocalizedError(String key) implements BaseFailure {
		@Override
		public String getMessage() {
			return "default message";
		}

		@Override
		public String getMessage(Locale locale) {
			return "localized: " + locale.getLanguage();
		}
	}

	@Nested
	@DisplayName("getMessage()")
	class GetMessageTests {

		@Test
		@DisplayName("returns the implemented message")
		void returnsImplementedMessage() {
			final var error = new NotFoundUser("123");
			assertEquals("User not found", error.getMessage());
		}
	}

	@Nested
	@DisplayName("getMessage(Locale)")
	class GetMessageLocaleTests {

		@Test
		@DisplayName("falls back to getMessage() when no i18n")
		void fallsBackToDefault() {
			final var error = new NotFoundUser("123");
			final Locale locale = Locale.ENGLISH;
			assertEquals("User not found", error.getMessage(locale));
		}

		@Test
		@DisplayName("returns localized message when overridden")
		void returnsLocalizedMessage() {
			final var error = new LocalizedError("test");
			final Locale locale = Locale.FRENCH;
			assertEquals("localized: fr", error.getMessage(locale));
		}
	}

	@Nested
	@DisplayName("getMessageArgs()")
	class GetMessageArgsTests {

		@Test
		@DisplayName("returns empty array by default")
		void returnsEmptyArrayByDefault() {
			final var error = new NotFoundUser("123");
			assertEquals(0, error.getMessageArgs().length);
		}

		@Test
		@DisplayName("returns custom args when overridden")
		void returnsCustomArgs() {
			final var error = new ParameterizedError("user-123");
			assertEquals(1, error.getMessageArgs().length);
			assertEquals("user-123", error.getMessageArgs()[0]);
		}
	}

	@Nested
	@DisplayName("getTitle()")
	class GetTitleTests {

		@Test
		@DisplayName("returns simple class name by default")
		void returnsSimpleClassName() {
			final var error = new NotFoundUser("123");
			assertEquals("NotFoundUser", error.getTitle());
		}

		@Test
		@DisplayName("returns custom title when overridden")
		void returnsCustomTitle() {
			final var error = new CustomTitleError();
			assertEquals("Custom Title", error.getTitle());
		}
	}

	@Nested
	@DisplayName("getErrorCode()")
	class GetErrorCodeTests {

		@Test
		@DisplayName("converts CamelCase to UPPER_SNAKE_CASE")
		void convertsCamelCaseToSnake() {
			final var error = new NotFoundUser("123");
			assertEquals("NOT_FOUND_USER", error.getErrorCode());
		}

		@Test
		@DisplayName("handles single word class name")
		void handlesSingleWord() {
			final var error = new SimpleError();
			assertEquals("SIMPLE_ERROR", error.getErrorCode());
		}

		@Test
		@DisplayName("handles consecutive uppercase letters in the middle")
		void handlesMiddleUppercase() {
			final var error = new IORetryError("read");
			final String code = error.getErrorCode();
			assertTrue(code.contains("RETRY"), "Expected code to contain RETRY, got: " + code);
			assertTrue(code.contains("ERROR"), "Expected code to contain ERROR, got: " + code);
		}

		@Test
		@DisplayName("handles anonymous class")
		void handlesAnonymousClass() {
			final BaseFailure anonymous = new BaseFailure() {
				@Override
				public String getMessage() {
					return "anon";
				}
			};
			final String code = anonymous.getErrorCode();
			assertNotNull(code);
		}
	}

	@Nested
	@DisplayName("getExtensions()")
	class GetExtensionsTests {

		@Test
		@DisplayName("returns empty map by default")
		void returnsEmptyMap() {
			final var error = new NotFoundUser("123");
			assertTrue(error.getExtensions().isEmpty());
		}

		@Test
		@DisplayName("returns custom extensions when overridden")
		void returnsCustomExtensions() {
			final var error = new CustomExtensionsError();
			final Map<String, Object> ext = error.getExtensions();
			assertEquals(3, ext.get("retryCount"));
			assertEquals("high", ext.get("severity"));
		}
	}

	@Nested
	@DisplayName("shouldLog()")
	class ShouldLogTests {

		@Test
		@DisplayName("returns true by default")
		void returnsTrueByDefault() {
			final var error = new NotFoundUser("123");
			assertTrue(error.shouldLog());
		}

		@Test
		@DisplayName("returns false when overridden")
		void returnsFalseWhenOverridden() {
			final var error = new NonLoggingError();
			assertFalse(error.shouldLog());
		}
	}

	@Nested
	@DisplayName("getLogLevel()")
	class GetLogLevelTests {

		@Test
		@DisplayName("returns WARN by default")
		void returnsWarnByDefault() {
			final var error = new NotFoundUser("123");
			assertEquals("WARN", error.getLogLevel());
		}

		@Test
		@DisplayName("returns custom level when overridden")
		void returnsCustomLevel() {
			final var error = new InfoLevelError();
			assertEquals("INFO", error.getLogLevel());
		}
	}
}
