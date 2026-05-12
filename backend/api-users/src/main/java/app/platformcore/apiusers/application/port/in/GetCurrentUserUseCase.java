package app.platformcore.apiusers.application.port.in;

import java.util.Objects;
import java.util.Set;

/**
 * Exposes the authenticated user's current account identity.
 *
 * <p>Implementations read the authenticated principal from the current execution context and
 * project only safe account fields needed by clients after login. Credential material, tokens, and
 * verification secrets must never cross this boundary.
 */
public interface GetCurrentUserUseCase {

  /**
   * Safe current-user projection returned by the application layer.
   *
   * @param id stable UUID string for the authenticated user
   * @param email email address associated with the authenticated account
   * @param active whether the account is currently usable by application policy
   * @param emailVerified whether the email-verification flow has completed
   * @param roles role names currently assigned to the account
   */
  record Result(String id, String email, boolean active, boolean emailVerified, Set<String> roles) {
    public Result {
      Objects.requireNonNull(id, "id cannot be null");
      Objects.requireNonNull(email, "email cannot be null");
      roles = Set.copyOf(Objects.requireNonNull(roles, "roles cannot be null"));
    }
  }

  /**
   * Reads the authenticated user's current account state.
   *
   * @return safe current-user projection for the authenticated principal
   */
  Result getCurrentUser();
}
