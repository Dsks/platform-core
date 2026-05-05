package app.qomo.apiusers.application.exception;

import java.util.Map;

/**
 * Represents an account state where the user must complete email verification before the requested
 * use case can continue.
 *
 * <p>This is an expected and usually recoverable application-layer business-state failure: the
 * client can typically request or follow a verification flow. HTTP adapters commonly map it to
 * {@code 403 Forbidden} or a contract-specific {@code 409 Conflict}. In login and verification
 * flows, preserve anti-enumeration guarantees by avoiding details that reveal whether a user or
 * verification token exists.
 */
public class UserNotVerifiedException extends ApplicationException {

  public UserNotVerifiedException() {
    super("USER_NOT_VERIFIED", "User account is not verified", Map.of());
  }
}
