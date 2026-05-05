package app.qomo.apiusers.application.port.out;

import java.util.Optional;
import java.util.Set;

/**
 * Abstracts access to the authenticated principal currently associated with execution.
 *
 * <p>The application expects implementations to translate framework-specific security context into
 * application-level identifiers and role names. Principal identifiers and roles are security data
 * and should not be logged except in deliberately scrubbed audit records.
 *
 * <p>This contract exposes context only; it must not perform authorization decisions or map
 * failures to HTTP responses.
 */
public interface CurrentUserPort {

  /**
   * Reads the current authenticated user identifier when one is available.
   *
   * @return current principal identifier, or empty when the execution is anonymous or
   *     unauthenticated
   */
  Optional<String> userId();

  /**
   * Reads the application-level roles assigned to the current principal.
   *
   * @return role names for the current principal, or an empty set when none are available
   */
  Set<String> roles();
}
