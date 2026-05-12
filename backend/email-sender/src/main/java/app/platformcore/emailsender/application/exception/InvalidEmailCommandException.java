package app.platformcore.emailsender.application.exception;

/**
 * Indicates that a parsed email command is semantically invalid for the application workflow.
 *
 * <p>This is raised after JSON parsing and structural validation, when application-level command
 * data still cannot be accepted, such as an event identifier that cannot be converted to a UUID.
 * The Kafka consumer treats this exception as an invalid command outcome and acknowledges the
 * message so it is discarded without retry.
 *
 * <p>The exception message is logged as an invalid-command reason, so it should remain a stable,
 * non-sensitive reason code and must not include raw payloads, email addresses, verification codes,
 * or other PII.
 */
public class InvalidEmailCommandException extends RuntimeException {

  /**
   * Reports the application validation reason for a command that should not continue processing.
   *
   * @param message stable, non-sensitive reason code that may be written to logs
   */
  public InvalidEmailCommandException(String message) {
    super(message);
  }
}
