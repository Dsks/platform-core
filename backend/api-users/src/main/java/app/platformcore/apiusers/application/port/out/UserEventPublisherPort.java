package app.platformcore.apiusers.application.port.out;

import app.platformcore.apiusers.domain.model.User;

/**
 * Abstracts publication of user lifecycle notifications emitted by application use cases.
 *
 * <p>Implementations may publish to messaging infrastructure or an operational log, but they must
 * treat user data as sensitive: email addresses, password hashes, and other personal data must not
 * be emitted in clear text unless a downstream contract explicitly requires it.
 *
 * <p>This contract does not decide when a user should be created, which roles are allowed, or how
 * publication failures map to transport responses.
 */
public interface UserEventPublisherPort {

  /**
   * Publishes that a user aggregate has been created.
   *
   * @param user created user aggregate; implementations should avoid exposing sensitive fields in
   *     payloads or logs
   */
  void userCreated(User user);
}
