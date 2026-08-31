package dev.jose.result.spring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FailureTest {

  record UserNotFound(long userId) implements Failure {
    @Override
    public String getMessage() {
      return "User was not found";
    }
  }

  record InvalidUser(String field) implements Failure {
    @Override
    public String getMessage() {
      return "Invalid user";
    }

    @Override
    public Object[] getMessageArgs() {
      return new Object[] {this.field};
    }

    @Override
    public String getTitle() {
      return "Invalid User";
    }

    @Override
    public String getErrorCode() {
      return "USER_INVALID";
    }

    @Override
    public Map<String, Object> getExtensions() {
      return Map.of("field", this.field);
    }
  }

  @Test
  void providesFrameworkAgnosticDefaults() {
    final Failure failure = new UserNotFound(42);

    assertEquals("User was not found", failure.getMessage());
    assertArrayEquals(new Object[0], failure.getMessageArgs());
    assertEquals("UserNotFound", failure.getTitle());
    assertEquals("USER_NOT_FOUND", failure.getErrorCode());
    assertTrue(failure.getExtensions().isEmpty());
  }

  @Test
  void allowsPresentationMetadataToBeCustomized() {
    final Failure failure = new InvalidUser("email");

    assertArrayEquals(new Object[] {"email"}, failure.getMessageArgs());
    assertEquals("Invalid User", failure.getTitle());
    assertEquals("USER_INVALID", failure.getErrorCode());
    assertEquals(Map.of("field", "email"), failure.getExtensions());
  }
}
