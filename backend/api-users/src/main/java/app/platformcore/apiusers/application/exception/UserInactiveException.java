package app.platformcore.apiusers.application.exception;

import java.util.Map;

/**
 * Represents an account state where the user exists but is not allowed to complete the requested
 * use case because the account is inactive.
 *
 * <p>This is an expected application-layer business-state failure, often terminal for the current
 * client action until reactivation or administrative intervention occurs. HTTP adapters should
 * generally map it to {@code 403 Forbidden}. In authentication and recovery flows, preserve
 * anti-enumeration behavior by avoiding responses that confirm whether a particular account exists
 * or is inactive.
 */
public class UserInactiveException extends ApplicationException {

  public UserInactiveException() {
    super("USER_INACTIVE", "User account is inactive", Map.of());
  }
}
