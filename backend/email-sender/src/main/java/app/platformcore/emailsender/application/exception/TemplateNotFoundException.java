package app.platformcore.emailsender.application.exception;

/**
 * Indicates that an email template required by an application flow is unavailable or cannot be
 * loaded.
 *
 * <p>This is raised while rendering an email before the message is sent. In the current command
 * processing flow, rendering failures are sanitized, persisted as failed email job state, and
 * acknowledged only after that safe state has been recorded.
 *
 * <p>Messages may be stored as part of failure details after sanitization, so they should identify
 * the missing or unreadable template without exposing rendered content, recipient data,
 * verification codes, filesystem details, or other sensitive values.
 */
public class TemplateNotFoundException extends RuntimeException {

  /**
   * Reports a template rendering failure without an underlying exception.
   *
   * @param message safe diagnostic text that may later appear in logs or persisted failure state
   */
  public TemplateNotFoundException(String message) {
    super(message);
  }

  /**
   * Reports a template rendering failure while preserving the original loading error for
   * diagnostics.
   *
   * @param message safe diagnostic text that may later appear in logs or persisted failure state
   * @param cause underlying template loading failure; it should not be used to carry sensitive
   *     rendered content or recipient-specific values
   */
  public TemplateNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
