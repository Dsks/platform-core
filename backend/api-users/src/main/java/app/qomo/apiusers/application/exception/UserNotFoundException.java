package app.qomo.apiusers.application.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Signals that a use case requires an existing user but no matching user was found.
 *
 * <p>This is an expected application-layer failure for user-management flows and should generally
 * map to {@code 404 Not Found} in HTTP adapters. Authentication, password recovery, and email
 * verification flows may require anti-enumeration behavior; in those cases callers or adapters
 * should use a generic response instead of revealing whether the user exists. The user identifier
 * can appear in diagnostics, so it must be sanitized and must not contain email addresses, tokens,
 * verification codes, or other secrets unless a downstream boundary explicitly redacts it.
 */
public final class UserNotFoundException extends ApplicationException {

  /**
   * Captures the unresolved user identifier used by the application use case.
   *
   * @param userId a sanitized, non-secret user identifier
   */
  public UserNotFoundException(String userId) {
    super(
        "USER_NOT_FOUND",
        "User not found",
        Map.of("userId", Objects.requireNonNull(userId, "userId cannot be null")));
  }
}
