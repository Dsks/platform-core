package app.qomo.apiusers.application.exception;

import java.util.Map;

public class UserInactiveException extends ApplicationException {

  public UserInactiveException() {
    super("USER_INACTIVE", "User account is inactive", Map.of());
  }
}