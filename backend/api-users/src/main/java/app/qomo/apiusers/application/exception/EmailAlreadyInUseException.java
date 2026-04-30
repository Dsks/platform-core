package app.qomo.apiusers.application.exception;

import java.util.Map;

public final class EmailAlreadyInUseException extends ApplicationException {

  public EmailAlreadyInUseException(String email) {
    super("USER_EMAIL_ALREADY_IN_USE", "Email already in use", Map.of("email", email));
  }
}
