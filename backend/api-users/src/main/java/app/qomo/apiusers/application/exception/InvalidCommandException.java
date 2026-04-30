package app.qomo.apiusers.application.exception;

import java.util.Map;
import java.util.Objects;

public final class InvalidCommandException extends ApplicationException {

  public InvalidCommandException(String field, String reason) {
    super(
        "INVALID_COMMAND",
        "Invalid command",
        Map.of(
            "field", Objects.requireNonNull(field, "field cannot be null"),
            "reason", Objects.requireNonNull(reason, "reason cannot be null")));
  }

  public static InvalidCommandException missing(String field) {
    return new InvalidCommandException(field, "missing");
  }

  public static InvalidCommandException blank(String field) {
    return new InvalidCommandException(field, "blank");
  }
}
