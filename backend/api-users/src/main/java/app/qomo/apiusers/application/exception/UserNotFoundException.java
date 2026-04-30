package app.qomo.apiusers.application.exception;

import java.util.Map;
import java.util.Objects;

public final class UserNotFoundException extends ApplicationException {

  public UserNotFoundException(String userId) {
    super(
        "USER_NOT_FOUND",
        "User not found",
        Map.of("userId", Objects.requireNonNull(userId, "userId cannot be null")));
  }
}
