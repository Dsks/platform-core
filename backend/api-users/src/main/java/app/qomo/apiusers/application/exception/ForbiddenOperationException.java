package app.qomo.apiusers.application.exception;

import java.util.Map;

/**
 * Represents a use-case decision that the current actor is not allowed to perform an operation.
 *
 * <p>This is an expected application-layer authorization failure, usually raised after the actor
 * has been identified and business permissions or ownership rules have been evaluated. HTTP
 * adapters should generally map it to {@code 403 Forbidden}. The reason can reach logs or error
 * responses, so it must be sanitized and must not reveal private resource details, secrets, or
 * whether protected users or tokens exist.
 */
public class ForbiddenOperationException extends ApplicationException {

  /**
   * Creates a forbidden-operation failure with a sanitized business reason.
   *
   * @param reason a client-safe explanation or stable reason code, never raw sensitive data
   */
  public ForbiddenOperationException(String reason) {
    super("FORBIDDEN_OPERATION", reason, Map.of("reason", reason));
  }
}
