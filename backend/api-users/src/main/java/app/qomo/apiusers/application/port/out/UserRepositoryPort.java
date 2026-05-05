package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Abstracts persistence for user aggregates and their assigned roles.
 *
 * <p>The application expects implementations to keep user identity, email, password hash,
 * activation, verification, login timestamps, and role assignments consistent when saving and
 * reading a user. Email addresses and password hashes are sensitive and must not be logged or
 * surfaced in clear text through infrastructure errors.
 *
 * <p>This contract does not decide registration, login, verification, or authorization rules; it
 * only provides the storage operations those use cases require.
 */
public interface UserRepositoryPort {

  /**
   * Persists the complete user aggregate and returns the stored representation.
   *
   * <p>Implementations should persist role assignments together with the user state so callers do
   * not observe a partially updated aggregate.
   *
   * @param user user aggregate to create or update
   * @return the persisted user representation
   */
  User save(User user);

  /**
   * Finds a user aggregate by its stable identifier.
   *
   * @param id user identifier assigned by the domain model
   * @return the user with its roles when present
   */
  Optional<User> findById(UserId id);

  /**
   * Finds a user aggregate by normalized email.
   *
   * @param email email value used by the application for identity lookup
   * @return the matching user with its roles when present
   */
  Optional<User> findByEmail(String email);

  /**
   * Checks whether an email is already assigned to a user.
   *
   * @param email email value to check
   * @return {@code true} when any user currently owns the email
   */
  boolean existsByEmail(String email);

  /**
   * Marks a user as email-verified at the supplied application time.
   *
   * @param id user to update
   * @param now timestamp to record as the verification update time
   */
  void setVerified(UserId id, Instant now);
}
