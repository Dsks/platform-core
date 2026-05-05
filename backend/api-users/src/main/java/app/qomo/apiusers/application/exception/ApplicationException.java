package app.qomo.apiusers.application.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Base type for expected, controlled failures raised by api-users application use cases.
 *
 * <p>Subclasses represent business or input conditions that the application layer can detect and
 * that an adapter, such as HTTP, may translate into a stable client response. The exception message
 * can be written to logs or surfaced by presentation code, so subclasses and callers must keep it
 * sanitized and must not include PII, passwords, tokens, verification codes, or other secrets.
 */
public abstract class ApplicationException extends RuntimeException {

  private final String code;
  private final Map<String, Object> params;

  /**
   * Initializes an application exception with a stable machine-readable code and optional
   * diagnostic parameters.
   *
   * <p>The {@code message} should be safe for logs and client-facing error payloads. Parameters are
   * intended for controlled diagnostics or response metadata and must follow the same sanitization
   * rules as the message.
   */
  protected ApplicationException(String code, String message, Map<String, Object> params) {
    super(Objects.requireNonNull(message, "message cannot be null"));
    this.code = Objects.requireNonNull(code, "code cannot be null");
    this.params = Objects.requireNonNull(params, "params cannot be null");
  }

  /**
   * Returns the stable application error code that adapters can use to map the failure to a client
   * response without parsing the human-readable message.
   */
  public String code() {
    return code;
  }

  /**
   * Returns structured, sanitized context for diagnostics or error payloads.
   *
   * <p>Values returned here may cross adapter boundaries; they must not contain secrets or
   * sensitive personal data unless the receiving boundary explicitly protects or redacts them.
   */
  public Map<String, Object> params() {
    return params;
  }
}
