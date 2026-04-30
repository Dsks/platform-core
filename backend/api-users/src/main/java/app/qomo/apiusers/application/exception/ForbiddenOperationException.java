package app.qomo.apiusers.application.exception;

import java.util.Map;

public class ForbiddenOperationException extends ApplicationException {

  public ForbiddenOperationException(String reason) {
    super("FORBIDDEN_OPERATION", reason, Map.of("reason", reason));
  }
}