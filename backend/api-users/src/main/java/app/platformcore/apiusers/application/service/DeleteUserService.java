package app.platformcore.apiusers.application.service;

import app.platformcore.apiusers.application.exception.ForbiddenOperationException;
import app.platformcore.apiusers.application.exception.InvalidCommandException;
import app.platformcore.apiusers.application.exception.UserNotFoundException;
import app.platformcore.apiusers.application.port.in.DeleteUserUseCase;
import app.platformcore.apiusers.application.port.out.ClockPort;
import app.platformcore.apiusers.application.port.out.UserRepositoryPort;
import app.platformcore.apiusers.domain.model.Email;
import app.platformcore.apiusers.domain.model.PasswordHash;
import app.platformcore.apiusers.domain.model.Role;
import app.platformcore.apiusers.domain.model.User;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Orchestrates administrative soft deletion and anonymization of user accounts. */
public final class DeleteUserService implements DeleteUserUseCase {

  private static final String USER_ROLE = "USER";
  private static final String ADMIN_ROLE = "ADMIN";
  private static final String SUPERADMIN_ROLE = "SUPERADMIN";

  private final UserRepositoryPort userRepository;
  private final ClockPort clock;

  /** Creates the service with the required persistence and time ports. */
  public DeleteUserService(UserRepositoryPort userRepository, ClockPort clock) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
  }

  /**
   * Applies administrative soft deletion after role and self-delete checks.
   *
   * @param command delete command with target and actor context
   */
  @Override
  public void delete(Command command) {
    if (command == null) {
      throw InvalidCommandException.missing("command");
    }

    User target =
        userRepository
            .findById(command.targetUserId())
            .orElseThrow(() -> new UserNotFoundException(command.targetUserId().toString()));

    if (command.actorUserId().equals(command.targetUserId())) {
      throw new ForbiddenOperationException("Users cannot delete themselves");
    }

    validateDeletePermission(normalizeRoles(command.actorRoles()), targetRoleNames(target));

    userRepository.softDeleteAndAnonymize(
        command.targetUserId(),
        Email.of("deleted-" + command.targetUserId() + "@deleted.app"),
        new PasswordHash("deleted:" + command.targetUserId()),
        clock.now());
  }

  private void validateDeletePermission(Set<String> actorRoles, Set<String> targetRoles) {
    if (targetRoles.contains(SUPERADMIN_ROLE)) {
      throw new ForbiddenOperationException("SUPERADMIN users cannot be deleted");
    }
    if (actorRoles.contains(SUPERADMIN_ROLE)
        && (targetRoles.contains(USER_ROLE) || targetRoles.contains(ADMIN_ROLE))) {
      return;
    }
    if (actorRoles.contains(ADMIN_ROLE)
        && targetRoles.contains(USER_ROLE)
        && !targetRoles.contains(ADMIN_ROLE)) {
      return;
    }
    throw new ForbiddenOperationException("Actor cannot delete the target user");
  }

  private Set<String> normalizeRoles(Set<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return Set.of();
    }
    return roles.stream()
        .filter(Objects::nonNull)
        .map(role -> role.trim().toUpperCase(Locale.ROOT))
        .filter(role -> !role.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<String> targetRoleNames(User user) {
    return user.roles().stream().map(Role::name).collect(Collectors.toUnmodifiableSet());
  }
}
