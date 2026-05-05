package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides the application read path for retrieving a user aggregate by id.
 *
 * <p>The service coordinates only the user repository port and does not add authorization,
 * projection, or transport-specific error mapping. Absence is propagated as {@link
 * Optional#empty()} so callers can decide how their adapter should represent a missing user.
 *
 * <p>The returned aggregate may contain personal data and account-state details; adapters should
 * avoid logging or exposing fields that are outside their read contract.
 */
public final class GetUserService implements GetUserUseCase {

  private final UserRepositoryPort userRepository;

  public GetUserService(UserRepositoryPort userRepository) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
  }

  /**
   * Looks up a user by its domain identifier without mutating account state.
   *
   * @param id typed user id to query; must be non-null
   * @return the matching user aggregate, or {@link Optional#empty()} when no user exists
   */
  @Override
  public Optional<User> getById(UserId id) {
    Objects.requireNonNull(id, "id cannot be null");
    return userRepository.findById(id);
  }
}
