package app.qomo.apiusers.application.exception;

import java.util.Map;

public class UserNotVerifiedException extends ApplicationException {

  public UserNotVerifiedException() {
    super("USER_NOT_VERIFIED", "User account is not verified", Map.of());
  }
}
