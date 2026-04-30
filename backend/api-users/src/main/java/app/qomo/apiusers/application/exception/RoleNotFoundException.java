package app.qomo.apiusers.application.exception;

import java.util.Map;
import java.util.Objects;

public final class RoleNotFoundException extends ApplicationException {

  public RoleNotFoundException(String roleName) {
    super(
        "ROLE_NOT_FOUND",
        "Role not found",
        Map.of("roleName", Objects.requireNonNull(roleName, "roleName cannot be null")));
  }
}
