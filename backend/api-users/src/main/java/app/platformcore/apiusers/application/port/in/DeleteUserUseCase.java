package app.platformcore.apiusers.application.port.in;

import app.platformcore.apiusers.domain.model.UserId;
import java.util.Objects;
import java.util.Set;

/** Defines the application boundary for administrative soft deletion of users. */
public interface DeleteUserUseCase {

  /** Administrative delete command with target and actor context. */
  record Command(UserId targetUserId, UserId actorUserId, Set<String> actorRoles) {
    public Command {
      Objects.requireNonNull(targetUserId, "targetUserId cannot be null");
      Objects.requireNonNull(actorUserId, "actorUserId cannot be null");
      actorRoles =
          actorRoles == null
              ? Set.of()
              : actorRoles.stream()
                  .filter(Objects::nonNull)
                  .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
  }

  /**
   * Soft deletes and anonymizes a user when the actor is allowed to perform the operation.
   *
   * @param command target user id, actor user id, and actor roles
   */
  void delete(Command command);
}
