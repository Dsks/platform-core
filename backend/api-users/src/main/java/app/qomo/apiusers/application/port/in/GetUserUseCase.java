package app.qomo.apiusers.application.port.in;

import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.util.Optional;

/**
 * Exposes user lookup by application-level user identity.
 *
 * <p>This port is intended for inbound adapters such as web controllers, internal jobs, consumers,
 * or application tests that need to read an already identified user. Implementations are
 * responsible for retrieving the aggregate through the application boundary. Transport parsing,
 * HTTP authorization decisions, response DTO mapping, and concrete persistence details remain
 * outside the port.
 *
 * <p>The returned domain object can contain personal data such as email and account state. Callers
 * should only project fields required by their adapter contract and should avoid logging the full
 * aggregate.
 */
public interface GetUserUseCase {

  /**
   * Looks up a user by its domain identifier.
   *
   * @param id typed user identifier; adapters are responsible for parsing external UUID strings
   *     before invoking the port
   * @return the matching user when present, or {@link Optional#empty()} when no user exists for the
   *     supplied identifier
   */
  Optional<User> getById(UserId id);
}
