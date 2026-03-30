package dev.jose.result;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NullMarked
class ValidatorTest {

	// ==================== Test Helpers ====================

	record Person(String name, int age, String email, Integer score) {
	}

	enum Severity {
		INFO, WARN, ERROR
	}

	record ValidationError(String code, String message, Severity severity) {
	}

	// ==================== Static Factory: of() ====================

	@Nested
	@DisplayName("Validator.of()")
	class OfTests {

		@Test
		@DisplayName("creates validator with target and String errors")
		void of_basicStringErrors() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));

			assertFalse(validator.hasErrors());
			assertEquals(0, validator.errorCount());
		}

		@Test
		@DisplayName("creates validator with target and custom error type")
		void of_basicCustomErrors() {
			final var validator = Validator
					.<Person, ValidationError>of(new Person("John", 30, "john@example.com", 100));

			assertFalse(validator.hasErrors());
			assertEquals(0, validator.errorCount());
		}

	}

	// ==================== Validation: validate() - eager ====================

	@Nested
	@DisplayName("Validator.validate() - eager")
	class ValidateEagerTests {

		@Test
		@DisplayName("condition passes: returns same instance, no error added (String)")
		void validateEager_passString() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.validate(p -> p.age() >= 18, "age", "Must be adult");

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}

		@Test
		@DisplayName("condition passes: returns same instance, no error added (custom type)")
		void validateEager_passCustom() {
			final var validator = Validator
					.<Person, ValidationError>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.validate(p -> p.age() >= 18, "age",
					new ValidationError("AGE_MIN", "Must be 18+", Severity.ERROR));

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}

		@Test
		@DisplayName("condition fails: returns new instance with error (String)")
		void validateEager_failString() {
			final var validator = Validator.<Person, String>of(new Person("John", 15, "john@example.com", 100));
			final var result = validator.validate(p -> p.age() >= 18, "age", "Must be adult");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
			assertEquals(1, result.errorCount());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, String> errors = ((Result.Failure<Person, Map<String, String>>) r).error();
			assertEquals("Must be adult", errors.get("age"));
		}

		@Test
		@DisplayName("condition fails: returns new instance with error (custom type)")
		void validateEager_failCustom() {
			final var validator = Validator
					.<Person, ValidationError>of(new Person("John", 15, "john@example.com", 100));
			final var result = validator.validate(p -> p.age() >= 18, "age",
					new ValidationError("AGE_MIN", "Must be 18+", Severity.ERROR));

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
			assertEquals(1, result.errorCount());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, ValidationError> errors = ((Result.Failure<Person, Map<String, ValidationError>>) r)
					.error();
			assertEquals("AGE_MIN", errors.get("age").code());
		}

		@Test
		@DisplayName("multiple failing validations accumulate errors across new instances")
		void validateEager_accumulates() {
			final var validator = Validator.<Person, String>of(new Person("", -5, "invalid", -10))
					.validate(p -> !p.name().isBlank(), "name", "Name is required")
					.validate(p -> p.age() >= 0, "age", "Age must be positive");

			assertTrue(validator.hasErrors());
			assertEquals(2, validator.errorCount());
		}

		@Test
		@DisplayName("original instance is not mutated after chaining failures")
		void validateEager_originalUnchanged() {
			final var original = Validator.<Person, String>of(new Person("John", 15, "john@example.com", 100));
			original.validate(p -> p.age() >= 18, "age", "Must be adult");

			assertFalse(original.hasErrors());
		}

		@Test
		@DisplayName("null condition: throws NullPointerException")
		@SuppressWarnings("all")
		void validateEager_nullCondition() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final Predicate<Person> nullCondition = null;

			assertThrows(NullPointerException.class, () -> validator.validate(nullCondition, "field", "error"));
		}

		@Test
		@DisplayName("null field: no error recorded when condition passes")
		@SuppressWarnings("all")
		void validateEager_nullField_conditionPasses() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.validate(_ -> true, null, "error");

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}
	}

	// ==================== Validation: validate() - lazy ====================

	@Nested
	@DisplayName("Validator.validate() - lazy")
	class ValidateLazyTests {

		@Test
		@DisplayName("condition passes: supplier not called, returns same instance")
		void validateLazy_pass() {
			final var supplierCalled = new AtomicBoolean(false);
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.validate(p -> p.age() >= 18, "age", () -> {
				supplierCalled.set(true);
				return "Must be adult";
			});

			assertSame(validator, result);
			assertFalse(result.hasErrors());
			assertFalse(supplierCalled.get());
		}

		@Test
		@DisplayName("condition fails: supplier called, returns new instance")
		void validateLazy_fail() {
			final var supplierCalled = new AtomicBoolean(false);
			final var validator = Validator.<Person, String>of(new Person("John", 15, "john@example.com", 100));
			final var result = validator.validate(p -> p.age() >= 18, "age", () -> {
				supplierCalled.set(true);
				return "Must be adult";
			});

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
			assertTrue(supplierCalled.get());
		}

		@Test
		@DisplayName("condition fails: custom error type via supplier")
		void validateLazy_customError() {
			final var supplierCalled = new AtomicBoolean(false);
			final var validator = Validator
					.<Person, ValidationError>of(new Person("John", 15, "john@example.com", 100));
			final var result = validator.validate(p -> p.age() >= 18, "age", () -> {
				supplierCalled.set(true);
				return new ValidationError("AGE_MIN", "Must be 18+", Severity.ERROR);
			});

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
			assertTrue(supplierCalled.get());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, ValidationError> errors = ((Result.Failure<Person, Map<String, ValidationError>>) r)
					.error();
			assertEquals("AGE_MIN", errors.get("age").code());
		}

		@Test
		@DisplayName("null supplier: no error recorded when condition passes")
		@SuppressWarnings("all")
		void validateLazy_nullSupplier_conditionPasses() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));

			final var result = validator.validate(_ -> true, "field", (Supplier<String>) null);

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}
	}

	// ==================== Validation: validateIf() ====================

	@Nested
	@DisplayName("Validator.validateIf()")
	class ValidateIfTests {

		@Test
		@DisplayName("condition true, inner validation passes: no errors")
		void validateIf_conditionTrue_passes() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.validateIf(p -> p.age() >= 18,
					v -> v.validate(p -> !p.name().isBlank(), "name", "Name required"));

			assertFalse(result.hasErrors());
		}

		@Test
		@DisplayName("condition false: validation skipped, returns same instance")
		void validateIf_conditionFalse_skipped() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.validateIf(p -> p.age() < 18,
					v -> v.validate(p -> !p.name().isBlank(), "name", "Name required"));

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}

		@Test
		@DisplayName("condition true, inner validation fails: error added")
		void validateIf_conditionTrue_fails() {
			final var validator = Validator.<Person, String>of(new Person("", 30, "john@example.com", 100));
			final var result = validator.validateIf(p -> p.age() >= 18,
					v -> v.validate(p -> !p.name().isBlank(), "name", "Name required"));

			assertTrue(result.hasErrors());
		}
	}

	// ==================== Validation: nonNull() ====================

	@Nested
	@DisplayName("Validator.nonNull()")
	class NonNullTests {

		@Test
		@DisplayName("null value: error added (String)")
		@SuppressWarnings("all")
		void nonNull_nullString() {
			final var validator = Validator.<Person, String>of(new Person(null, 30, "john@example.com", 100));
			final var result = validator.nonNull(Person::name, "name", "Field must not be null");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, String> errors = ((Result.Failure<Person, Map<String, String>>) r).error();
			assertEquals("Field must not be null", errors.get("name"));
		}

		@Test
		@DisplayName("null value: error added (custom type)")
		@SuppressWarnings("all")
		void nonNull_nullCustom() {
			final var validator = Validator.<Person, ValidationError>of(new Person(null, 30, "john@example.com", 100));
			final var result = validator.nonNull(Person::name, "name",
					new ValidationError("NULL_FIELD", "Field must not be null", Severity.ERROR));

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, ValidationError> errors = ((Result.Failure<Person, Map<String, ValidationError>>) r)
					.error();
			assertEquals("NULL_FIELD", errors.get("name").code());
		}

		@Test
		@DisplayName("non-null value: returns same instance, no error")
		void nonNull_valid() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.nonNull(Person::name, "name", "Field must not be null");

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}
	}

	// ==================== Validation: matches() ====================

	@Nested
	@DisplayName("Validator.matches()")
	class MatchesTests {

		@Test
		@DisplayName("null value: treated as failure, error added")
		@SuppressWarnings("all")
		void matches_null() {
			final var validator = Validator.<Person, String>of(new Person(null, 30, "john@example.com", 100));
			final var result = validator.matches(Person::name, "^[A-Za-z]+$", "name", "Invalid name");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
		}

		@Test
		@DisplayName("value matches pattern: returns same instance, no error")
		void matches_valid() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.matches(Person::name, "^[A-Za-z]+$", "name", "Invalid name");

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}

		@Test
		@DisplayName("value does not match: error added (String)")
		void matches_invalidString() {
			final var validator = Validator.<Person, String>of(new Person("John123", 30, "john@example.com", 100));
			final var result = validator.matches(Person::name, "^[A-Za-z]+$", "name", "Invalid name");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, String> errors = ((Result.Failure<Person, Map<String, String>>) r).error();
			assertEquals("Invalid name", errors.get("name"));
		}

		@Test
		@DisplayName("value does not match: error added (custom type)")
		void matches_invalidCustom() {
			final var validator = Validator
					.<Person, ValidationError>of(new Person("John123", 30, "john@example.com", 100));
			final var result = validator.matches(Person::name, "^[A-Za-z]+$", "name",
					new ValidationError("INVALID_FORMAT", "Name must be alphabetic", Severity.ERROR));

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, ValidationError> errors = ((Result.Failure<Person, Map<String, ValidationError>>) r)
					.error();
			assertEquals("INVALID_FORMAT", errors.get("name").code());
		}
	}

	// ==================== Validation: range() ====================

	@Nested
	@DisplayName("Validator.range()")
	class RangeTests {

		@Test
		@DisplayName("null value: treated as failure, error added")
		@SuppressWarnings("all")
		void range_null() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", null));
			final var result = validator.range(Person::score, 0, 100, "score", "Score out of range");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
		}

		@Test
		@DisplayName("value below min: error added")
		void range_belowMin() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", -5));
			final var result = validator.range(Person::score, 0, 100, "score", "Score out of range");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, String> errors = ((Result.Failure<Person, Map<String, String>>) r).error();
			assertEquals("Score out of range", errors.get("score"));
		}

		@Test
		@DisplayName("value above max: error added")
		void range_aboveMax() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 150));
			final var result = validator.range(Person::score, 0, 100, "score", "Score out of range");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
		}

		@Test
		@DisplayName("value in range: returns same instance, no error")
		void range_valid() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 50));
			final var result = validator.range(Person::score, 0, 100, "score", "Score out of range");

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}

		@Test
		@DisplayName("boundary values (inclusive): returns same instance, no error")
		void range_boundaries() {
			final var atMin = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 0))
					.range(Person::score, 0, 100, "score", "Score out of range");
			final var atMax = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100))
					.range(Person::score, 0, 100, "score", "Score out of range");

			assertFalse(atMin.hasErrors());
			assertFalse(atMax.hasErrors());
		}
	}

	// ==================== Validation: length() ====================

	@Nested
	@DisplayName("Validator.length()")
	class LengthTests {

		@Test
		@DisplayName("null value: treated as failure, error added")
		@SuppressWarnings("all")
		void length_null() {
			final var validator = Validator.<Person, String>of(new Person(null, 30, "john@example.com", 100));
			final var result = validator.length(Person::name, 1, 50, "name", "Name length invalid");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
		}

		@Test
		@DisplayName("value too short: error added")
		void length_tooShort() {
			final var validator = Validator.<Person, String>of(new Person("A", 30, "john@example.com", 100));
			final var result = validator.length(Person::name, 2, 50, "name", "Name length invalid");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());

			final var r = result.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, String> errors = ((Result.Failure<Person, Map<String, String>>) r).error();
			assertEquals("Name length invalid", errors.get("name"));
		}

		@Test
		@DisplayName("value too long: error added")
		void length_tooLong() {
			final var validator = Validator
					.<Person, String>of(new Person("A".repeat(100), 30, "john@example.com", 100));
			final var result = validator.length(Person::name, 2, 50, "name", "Name length invalid");

			assertNotSame(validator, result);
			assertTrue(result.hasErrors());
		}

		@Test
		@DisplayName("value within bounds: returns same instance, no error")
		void length_valid() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = validator.length(Person::name, 1, 50, "name", "Name length invalid");

			assertSame(validator, result);
			assertFalse(result.hasErrors());
		}
	}

	// ==================== Terminal: result() ====================

	@Nested
	@DisplayName("Validator.result()")
	class ResultTests {

		@Test
		@DisplayName("no errors: returns Success wrapping target")
		void result_success() {
			final var person = new Person("John", 30, "john@example.com", 100);
			final var result = Validator.<Person, String>of(person).result();

			assertTrue(result.isSuccess());
			assertEquals(person, result.unwrap());
		}

		@Test
		@DisplayName("has errors: returns Failure with unmodifiable map (String)")
		void result_failureString() {
			final var result = Validator.<Person, String>of(new Person("", -5, "invalid", -10))
					.validate(p -> !p.name().isBlank(), "name", "Name required")
					.validate(p -> p.age() >= 0, "age", "Age must be positive").result();

			assertTrue(result.isFailure());

			assertInstanceOf(Result.Failure.class, result);
			final Map<String, String> errors = ((Result.Failure<Person, Map<String, String>>) result).error();
			assertTrue(errors.containsKey("name"));
			assertTrue(errors.containsKey("age"));
		}

		@Test
		@DisplayName("has errors: returns Failure with unmodifiable map (custom type)")
		void result_failureCustom() {
			final var result = Validator.<Person, ValidationError>of(new Person("", -5, "invalid", -10))
					.validate(p -> !p.name().isBlank(), "name",
							new ValidationError("BLANK_NAME", "Name required", Severity.ERROR))
					.validate(p -> p.age() >= 0, "age",
							new ValidationError("NEGATIVE_AGE", "Age must be positive", Severity.ERROR))
					.result();

			assertTrue(result.isFailure());

			assertInstanceOf(Result.Failure.class, result);
			final Map<String, ValidationError> errors = ((Result.Failure<Person, Map<String, ValidationError>>) result)
					.error();
			assertEquals("BLANK_NAME", errors.get("name").code());
			assertEquals("NEGATIVE_AGE", errors.get("age").code());
		}

		@Test
		@DisplayName("result error map is unmodifiable")
		@SuppressWarnings("all")
		void result_errorMapUnmodifiable() {
			final var result = Validator.<Person, String>of(new Person("", 30, "john@example.com", 100))
					.validate(p -> !p.name().isBlank(), "name", "Name required").result();

			assertInstanceOf(Result.Failure.class, result);
			final Map<String, String> errors = ((Result.Failure<Person, Map<String, String>>) result).error();
			assertThrows(UnsupportedOperationException.class, () -> errors.put("extra", "boom"));
		}
	}

	// ==================== Terminal: resultOr() ====================

	@Nested
	@DisplayName("Validator.resultOr()")
	class ResultOrTests {

		@Test
		@DisplayName("no errors: returns Success")
		void resultOr_success() {
			final var result = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100))
					.resultOr(errors -> "Validation failed: " + errors.size());

			assertTrue(result.isSuccess());
		}

		@Test
		@DisplayName("has errors: maps to custom error (String)")
		void resultOr_failureString() {
			final var result = Validator.<Person, String>of(new Person("", 30, "john@example.com", 100))
					.validate(p -> !p.name().isBlank(), "name", "Name required")
					.resultOr(errors -> "Invalid: " + errors.keySet());

			assertTrue(result.isFailure());
			assertTrue(result.fold(_ -> "", e -> e).contains("Invalid:"));
		}

		@Test
		@DisplayName("has errors: maps error map to count (custom type)")
		void resultOr_failureCustom() {
			final var result = Validator.<Person, ValidationError>of(new Person("", 30, "john@example.com", 100))
					.validate(p -> !p.name().isBlank(), "name",
							new ValidationError("BLANK", "Name required", Severity.ERROR))
					.resultOr(Map::size);

			assertTrue(result.isFailure());
			assertEquals(Integer.valueOf(1), result.fold(_ -> 0, e -> e));
		}
	}

	// ==================== Terminal: errors() ====================

	@Nested
	@DisplayName("Validator.errors()")
	class ErrorsTests {

		@Test
		@DisplayName("no errors: returns empty unmodifiable map")
		void errors_empty() {
			final var errors = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100)).errors();

			assertTrue(errors.isEmpty());
		}

		@Test
		@DisplayName("has errors: returns populated map with all fields")
		void errors_populated() {
			final var errors = Validator.<Person, String>of(new Person("", -5, "invalid", -10))
					.validate(p -> !p.name().isBlank(), "name", "Name required")
					.validate(p -> p.age() >= 0, "age", "Age must be positive").errors();

			assertEquals(2, errors.size());
			assertEquals("Name required", errors.get("name"));
			assertEquals("Age must be positive", errors.get("age"));
		}

		@Test
		@DisplayName("returned map is unmodifiable")
		@SuppressWarnings("all")
		void errors_unmodifiable() {
			final var errors = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100))
					.validate(p -> p.age() >= 150, "age", "Must be elder").errors();

			assertThrows(UnsupportedOperationException.class, () -> errors.put("new", "error"));
		}
	}

	// ==================== Terminal: hasErrors() ====================

	@Nested
	@DisplayName("Validator.hasErrors()")
	class HasErrorsTests {

		@Test
		@DisplayName("no errors: returns false")
		void hasErrors_false() {
			assertFalse(Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100)).hasErrors());
		}

		@Test
		@DisplayName("has errors: returns true")
		void hasErrors_true() {
			assertTrue(Validator.<Person, String>of(new Person("", 30, "john@example.com", 100))
					.validate(p -> !p.name().isBlank(), "name", "Name required").hasErrors());
		}
	}

	// ==================== Terminal: errorCount() ====================

	@Nested
	@DisplayName("Validator.errorCount()")
	class ErrorCountTests {

		@Test
		@DisplayName("no errors: returns 0")
		void errorCount_zero() {
			assertEquals(0, Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100)).errorCount());
		}

		@Test
		@DisplayName("has errors: returns correct count")
		void errorCount_multiple() {
			final var count = Validator.<Person, String>of(new Person("", -5, "", -10))
					.validate(p -> !p.name().isBlank(), "name", "Name required")
					.validate(p -> p.age() >= 0, "age", "Age must be positive").errorCount();

			assertEquals(2, count);
		}
	}

	// ==================== Static: compose() ====================

	@Nested
	@DisplayName("Validator.compose()")
	class ComposeTests {

		@Test
		@DisplayName("applies multiple failing validations, accumulates errors (String)")
		void compose_basicString() {
			final var validator = Validator.<Person, String>compose(new Person("", -5, "invalid", -10),
					v -> v.validate(p -> !p.name().isBlank(), "name", "Name required"),
					v -> v.validate(p -> p.age() >= 0, "age", "Age must be positive"));

			assertTrue(validator.hasErrors());
			assertEquals(2, validator.errorCount());
		}

		@Test
		@DisplayName("applies multiple failing validations, accumulates errors (custom type)")
		void compose_basicCustom() {
			final var validator = Validator.<Person, ValidationError>compose(new Person("", -5, "invalid", -10),
					v -> v.validate(p -> !p.name().isBlank(), "name",
							new ValidationError("BLANK", "Name required", Severity.ERROR)),
					v -> v.validate(p -> p.age() >= 0, "age",
							new ValidationError("NEGATIVE", "Age must be positive", Severity.ERROR)));

			assertTrue(validator.hasErrors());
			assertEquals(2, validator.errorCount());
		}

		@Test
		@DisplayName("empty varargs: returns valid validator with no errors")
		void compose_empty() {
			final var validator = Validator.<Person, String>compose(new Person("John", 30, "john@example.com", 100));

			assertFalse(validator.hasErrors());
		}

		@Test
		@DisplayName("single validation: works correctly")
		void compose_single() {
			final var validator = Validator.<Person, String>compose(new Person("", 30, "john@example.com", 100),
					v -> v.validate(p -> !p.name().isBlank(), "name", "Name required"));

			assertTrue(validator.hasErrors());
		}
	}

	// ==================== Immutability ====================

	@Nested
	@DisplayName("Immutability")
	class ImmutabilityTests {

		@Test
		@DisplayName("passing condition: returns same instance (no allocation)")
		void immutability_passReturnsSame() {
			final var original = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100));
			final var result = original.validate(p -> p.age() >= 18, "age", "Must be adult");

			assertSame(original, result);
		}

		@Test
		@DisplayName("failing condition: returns new instance")
		void immutability_failReturnsNew() {
			final var original = Validator.<Person, String>of(new Person("John", 15, "john@example.com", 100));
			final var result = original.validate(p -> p.age() >= 18, "age", "Must be adult");

			assertNotSame(original, result);
		}

		@Test
		@DisplayName("original instance unaffected after downstream failures")
		void immutability_originalUnchanged() {
			final var base = Validator.<Person, String>of(new Person("John", 15, "john@example.com", 100));
			final var withAge = base.validate(p -> p.age() >= 18, "age", "Must be adult");
			final var withName = withAge.validate(p -> !p.name().isBlank(), "name", "Name required");

			assertFalse(base.hasErrors());
			assertEquals(1, withAge.errorCount());
			assertEquals(1, withName.errorCount()); // name passes, no additional error
		}

		@Test
		@DisplayName("branching from same base produces independent validators")
		void immutability_branchingIndependent() {
			final var base = Validator.<Person, String>of(new Person("", 15, "john@example.com", 100));

			final var branchA = base.validate(p -> p.age() >= 18, "age", "Must be adult");
			final var branchB = base.validate(p -> !p.name().isBlank(), "name", "Name required");

			assertFalse(base.hasErrors());
			assertEquals(1, branchA.errorCount());
			assertTrue(branchA.errors().containsKey("age"));
			assertEquals(1, branchB.errorCount());
			assertTrue(branchB.errors().containsKey("name"));
		}
	}

	// ==================== Edge Cases ====================

	@Nested
	@DisplayName("Edge Cases")
	class EdgeCaseTests {

		@Test
		@DisplayName("chaining mixed passing and failing validations")
		void chaining_mixedResults() {
			final var validator = Validator.<Person, String>of(new Person("John", 30, "john@example.com", 100))
					.nonNull(Person::email, "email", "Email required").range(Person::age, 0, 150, "age", "Age invalid")
					.range(Person::score, 0, 100, "score", "Score invalid");

			assertFalse(validator.hasErrors());
			assertTrue(validator.result().isSuccess());
		}

		@Test
		@DisplayName("all validations fail: errors map contains all fields (String)")
		void allFail_string() {
			final var validator = Validator.<Person, String>of(new Person("", 200, "invalid-email", -100))
					.validate(p -> !p.name().isBlank(), "name", "Name required")
					.range(Person::age, 0, 150, "age", "Age invalid")
					.matches(Person::email, "^[^@]+@[^@]+$", "email", "Invalid email")
					.range(Person::score, 0, 100, "score", "Score invalid");

			assertTrue(validator.hasErrors());
			assertEquals(4, validator.errorCount());

			final var r = validator.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, String> errors = ((Result.Failure<Person, Map<String, String>>) r).error();
			assertTrue(errors.containsKey("name"));
			assertTrue(errors.containsKey("age"));
			assertTrue(errors.containsKey("email"));
			assertTrue(errors.containsKey("score"));
		}

		@Test
		@DisplayName("all validations fail: errors map contains all fields (custom type)")
		void allFail_custom() {
			final var validator = Validator.<Person, ValidationError>of(new Person("", 200, "invalid-email", -100))
					.validate(p -> !p.name().isBlank(), "name",
							new ValidationError("BLANK", "Name required", Severity.ERROR))
					.range(Person::age, 0, 150, "age", new ValidationError("RANGE", "Age invalid", Severity.ERROR))
					.matches(Person::email, "^[^@]+@[^@]+$", "email",
							new ValidationError("FORMAT", "Invalid email", Severity.ERROR))
					.range(Person::score, 0, 100, "score",
							new ValidationError("RANGE", "Score invalid", Severity.ERROR));

			assertTrue(validator.hasErrors());
			assertEquals(4, validator.errorCount());

			final var r = validator.result();
			assertInstanceOf(Result.Failure.class, r);
			final Map<String, ValidationError> errors = ((Result.Failure<Person, Map<String, ValidationError>>) r)
					.error();
			assertTrue(errors.values().stream().allMatch(e -> e.severity() == Severity.ERROR));
		}

		@Test
		@DisplayName("same field validated twice: last failure wins")
		void sameField_lastWins() {
			final var validator = Validator.<Person, String>of(new Person("A", 30, "john@example.com", 100))
					.validate(p -> p.name().length() >= 3, "name", "Too short")
					.validate(p -> p.name().length() >= 5, "name", "Way too short");

			assertTrue(validator.hasErrors());
			assertEquals(1, validator.errorCount());
			assertEquals("Way too short", validator.errors().get("name"));
		}
	}
}
