package app.platformcore.emailsender.application.util;

/**
 * Builds bounded error text for application use cases that need to persist or emit failure details
 * without carrying known sensitive values verbatim.
 *
 * <p>{@code ProcessEmailCommandService} uses this helper when a send attempt fails after rendering
 * a verification payload, passing the verification code so it can be redacted from the stored
 * failure reason and warning log. {@code RetryEmailJobsService} uses the same format when a
 * retryable job fails again and the error must be persisted for later diagnosis.
 *
 * <p>The helper expects the exception itself to be non-null. The optional sensitive value is
 * treated as an exact string to remove when it is present and non-blank. It does not inspect
 * exception causes, parse structured payloads, detect arbitrary secrets, hash, encrypt, or provide
 * a general privacy boundary; callers remain responsible for deciding which value, if any, must be
 * redacted.
 */
public final class ErrorSanitizer {

  private static final int MAX_ERROR_LENGTH = 500;

  private ErrorSanitizer() {}

  /**
   * Converts an application failure into a compact string shaped as the exception simple class
   * name, optionally followed by its non-blank message.
   *
   * <p>If {@code sensitiveValue} is non-null and non-blank, every exact occurrence is replaced with
   * {@code [REDACTED]}. The final text is capped to the application failure-message limit, so very
   * large provider or template errors cannot expand persisted state or logs indefinitely. Blank
   * exception messages are omitted, and blank sensitive values intentionally do not trigger
   * redaction.
   *
   * @param exception non-null runtime failure to describe
   * @param sensitiveValue optional exact value that must not appear verbatim in the returned text
   * @return sanitized, bounded failure text suitable for job state and application logs
   */
  public static String sanitize(RuntimeException exception, String sensitiveValue) {
    String error = exception.getClass().getSimpleName();
    String message = exception.getMessage();
    if (message != null && !message.isBlank()) {
      error = error + ": " + message;
    }

    String sanitized = error;
    if (sensitiveValue != null && !sensitiveValue.isBlank()) {
      sanitized = sanitized.replace(sensitiveValue, "[REDACTED]");
    }

    if (sanitized.length() > MAX_ERROR_LENGTH) {
      sanitized = sanitized.substring(0, MAX_ERROR_LENGTH);
    }

    return sanitized;
  }
}
