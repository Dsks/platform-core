package app.qomo.apiusers.application.exception;

import java.util.Map;
import java.util.Objects;

public abstract class ApplicationException extends RuntimeException {

  private final String code;
  private final Map<String, Object> params;

  protected ApplicationException(String code, String message, Map<String, Object> params) {
    super(Objects.requireNonNull(message, "message cannot be null"));
    this.code = Objects.requireNonNull(code, "code cannot be null");
    this.params = Objects.requireNonNull(params, "params cannot be null");
  }

  public String code() {
    return code;
  }

  public Map<String, Object> params() {
    return params;
  }
}
