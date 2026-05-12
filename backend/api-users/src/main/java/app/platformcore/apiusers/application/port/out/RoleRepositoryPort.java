package app.platformcore.apiusers.application.port.out;

import app.platformcore.apiusers.domain.model.Role;
import java.util.Optional;

/**
 * Abstracts lookup of roles known to the user application.
 *
 * <p>The application expects implementations to resolve role names consistently with the platform's
 * canonical naming rules. This contract only retrieves roles; deciding whether a caller may assign
 * a role belongs to application authorization logic.
 */
public interface RoleRepositoryPort {

  /**
   * Finds a role by its application-level name.
   *
   * @param name role name requested by a use case
   * @return the matching role when it exists
   */
  Optional<Role> findByName(String name);
}
