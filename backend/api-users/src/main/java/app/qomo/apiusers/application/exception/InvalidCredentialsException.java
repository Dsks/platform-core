package app.qomo.apiusers.application.exception;

import java.util.Map;

public class InvalidCredentialsException extends ApplicationException {

  public InvalidCredentialsException() {
    super("INVALID_CREDENTIALS", "Invalid credentials", Map.of());
  }
}