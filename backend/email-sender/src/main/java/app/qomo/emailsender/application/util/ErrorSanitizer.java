package app.qomo.emailsender.application.util;

public final class ErrorSanitizer {

  private static final int MAX_ERROR_LENGTH = 500;

  private ErrorSanitizer() {
  }

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