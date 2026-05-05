package app.qomo.apiusers.application.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Signals that an application command failed use-case input validation before business processing.
 *
 * <p>This is an expected application-layer failure and is normally raised by command handlers or
 * application validators, not by transport adapters. HTTP adapters should generally map it to
 * {@code 400 Bad Request}. The field and reason can appear in logs or response payloads, so they
 * must be stable, non-sensitive identifiers rather than raw user input, credentials, tokens, or
 * verification codes.
 */
public final class InvalidCommandException extends ApplicationException {

  /**
   * Builds a validation failure for a specific command field.
   *
   * @param field a sanitized command field name or path
   * @param reason a stable, sanitized reason suitable for clients and logs
   */
  public InvalidCommandException(String field, String reason) {
    super(
        "INVALID_COMMAND",
        "Invalid command",
        Map.of(
            "field", Objects.requireNonNull(field, "field cannot be null"),
            "reason", Objects.requireNonNull(reason, "reason cannot be null")));
  }

  /**
   * Shortcut for required command fields that were not provided.
   *
   * @param field a sanitized command field name or path
   */
  public static InvalidCommandException missing(String field) {
    return new InvalidCommandException(field, "missing");
  }

  /**
   * Shortcut for command fields that were provided without meaningful content.
   *
   * @param field a sanitized command field name or path
   */
  public static InvalidCommandException blank(String field) {
    return new InvalidCommandException(field, "blank");
  }
}
