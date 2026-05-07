package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.exception.InvalidCredentialsException;
import app.qomo.apiusers.application.exception.UserInactiveException;
import app.qomo.apiusers.application.exception.UserNotFoundException;
import app.qomo.apiusers.application.port.in.GetCurrentUserUseCase;
import app.qomo.apiusers.application.port.out.CurrentUserPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.UserId;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Provides the read path for the authenticated user's own identity.
 *
 * <p>The service uses the security-context port only to discover the current principal id, then
 * reloads the user aggregate from persistence so the response reflects current account state and
 * role assignments rather than only the JWT claims.
 */
public final class GetCurrentUserService implements GetCurrentUserUseCase {

  private final CurrentUserPort currentUser;
  private final UserRepositoryPort userRepository;

  public GetCurrentUserService(CurrentUserPort currentUser, UserRepositoryPort userRepository) {
    this.currentUser = Objects.requireNonNull(currentUser, "currentUser cannot be null");
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
  }

  /**
   * Resolves the authenticated principal into a current user projection.
   *
   * @return safe current-user data for the authenticated principal
   * @throws InvalidCredentialsException when no authenticated principal id is available or it is
   *     not a user UUID
   * @throws UserNotFoundException when the authenticated user no longer exists
   * @throws UserInactiveException when the authenticated user account is inactive
   */
  @Override
  public Result getCurrentUser() {
    String principalId = currentUser.userId().orElseThrow(InvalidCredentialsException::new);
    UserId userId = parseUserId(principalId);

    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId.toString()));

    if (!user.isActive()) {
      throw new UserInactiveException();
    }

    return new Result(
        user.id().toString(),
        user.email().value(),
        user.isActive(),
        user.isVerified(),
        user.roles().stream().map(Role::name).collect(Collectors.toSet()));
  }

  private UserId parseUserId(String principalId) {
    try {
      return UserId.of(principalId);
    } catch (IllegalArgumentException ex) {
      throw new InvalidCredentialsException();
    }
  }
}
