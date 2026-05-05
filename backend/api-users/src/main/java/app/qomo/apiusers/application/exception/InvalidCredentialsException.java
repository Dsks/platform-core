package app.qomo.apiusers.application.exception;

import java.util.Map;

/**
 * Signals that an authentication attempt failed without disclosing which credential was invalid.
 *
 * <p>This is an expected, recoverable application-layer failure for login-like use cases. HTTP
 * adapters should generally map it to {@code 401 Unauthorized}. To preserve anti-enumeration
 * guarantees, callers must not replace the generic message with details about whether the user,
 * password, token, or verification state was the failing part.
 */
public class InvalidCredentialsException extends ApplicationException {

  public InvalidCredentialsException() {
    super("INVALID_CREDENTIALS", "Invalid credentials", Map.of());
  }
}
