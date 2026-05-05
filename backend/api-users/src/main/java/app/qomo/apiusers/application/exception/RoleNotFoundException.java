package app.qomo.apiusers.application.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Signals that a use case referenced a role that the application cannot resolve.
 *
 * <p>This is a controlled application-layer failure for role assignment or permission-management
 * flows. If the role name came from client input, HTTP adapters should generally map it to {@code
 * 404 Not Found} or {@code 400 Bad Request}, depending on the API contract; if it came from
 * internal configuration, it should be treated as a server-side setup defect. The role name can be
 * logged or returned, so callers should pass a sanitized role identifier rather than arbitrary user
 * input or sensitive authorization details.
 */
public final class RoleNotFoundException extends ApplicationException {

  /**
   * Captures the missing role identifier used by the application use case.
   *
   * @param roleName a sanitized role name or stable role identifier
   */
  public RoleNotFoundException(String roleName) {
    super(
        "ROLE_NOT_FOUND",
        "Role not found",
        Map.of("roleName", Objects.requireNonNull(roleName, "roleName cannot be null")));
  }
}
