package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.exception.ForbiddenOperationException;
import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.UserNotFoundException;
import app.qomo.apiusers.application.port.in.GetUserUseCase;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides application read paths for user lookup and administrative user listing.
 *
 * <p>The returned aggregate may contain personal data and account-state details; adapters should
 * avoid logging or exposing fields that are outside their read contract.
 */
public final class GetUserService implements GetUserUseCase {

  private static final int MAX_PAGE_SIZE = 100;
  private static final String USER_ROLE = "USER";
  private static final String ADMIN_ROLE = "ADMIN";
  private static final String SUPERADMIN_ROLE = "SUPERADMIN";
  private static final Set<String> ADMIN_VISIBLE_ROLES = Set.of(USER_ROLE, ADMIN_ROLE);

  private final UserRepositoryPort userRepository;
  private final ClockPort clock;

  /** Creates the service with system time for callers that do not inject a clock port. */
  public GetUserService(UserRepositoryPort userRepository) {
    this(userRepository, java.time.Instant::now);
  }

  /** Creates the service with an explicit time port for account-state updates. */
  public GetUserService(UserRepositoryPort userRepository, ClockPort clock) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
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

  /**
   * Applies a partial user update without toggling state.
   *
   * <p>ADMIN actors may update USER accounts only. SUPERADMIN actors may update USER and ADMIN
   * accounts; SUPERADMIN targets are intentionally blocked in this first block until a finer
   * self-protection policy is defined.
   */
  @Override
  public User update(UpdateCommand command) {
    if (command == null) {
      throw InvalidCommandException.missing("command");
    }
    if (command.active() == null) {
      throw InvalidCommandException.missing("active");
    }

    User target =
        userRepository
            .findById(command.id())
            .orElseThrow(() -> new UserNotFoundException(command.id().toString()));

    validateUpdatePermission(normalizeRoles(command.actorRoles()), targetRoleNames(target));
    validateSelfDeactivation(command, target);

    if (command.active()) {
      target.activate(clock.now());
    } else {
      target.deactivate(clock.now());
    }

    return userRepository.save(target);
  }

  @Override
  public PageResult listForAdmin(Query query, Set<String> actorRoles) {
    if (query == null) {
      throw InvalidCommandException.missing("query");
    }
    if (query.page() < 0) {
      throw InvalidCommandException.missing("page");
    }
    if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
      throw InvalidCommandException.missing("size");
    }

    Set<String> roles = normalizeRoles(actorRoles);
    String emailSearch = normalizeSearch(query.search());
    String role = normalizeRoleFilter(query.role());
    DeletedFilter deleted = query.deleted();
    UserRepositoryPort.Page<User> page =
        roles.contains(SUPERADMIN_ROLE)
            ? userRepository.findAllPage(
                emailSearch,
                deleted,
                query.active(),
                query.verified(),
                role,
                query.sortBy(),
                query.sortDirection(),
                query.page(),
                query.size())
            : roles.contains(ADMIN_ROLE)
                ? userRepository.findPageByVisibleRoles(
                    ADMIN_VISIBLE_ROLES,
                    emailSearch,
                    deleted,
                    query.active(),
                    query.verified(),
                    role,
                    query.sortBy(),
                    query.sortDirection(),
                    query.page(),
                    query.size())
                : rejectForbidden();

    return new PageResult(
        page.content().stream().map(this::toSummary).toList(),
        page.page(),
        page.size(),
        page.totalElements(),
        page.totalPages());
  }

  private UserRepositoryPort.Page<User> rejectForbidden() {
    throw new ForbiddenOperationException("Only ADMIN and SUPERADMIN can list users");
  }

  private String normalizeSearch(String search) {
    if (search == null) {
      return null;
    }
    String normalized = search.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeRoleFilter(String role) {
    if (role == null) {
      return null;
    }
    String normalized = role.trim().toUpperCase(Locale.ROOT);
    return normalized.isBlank() ? null : normalized;
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

  private void validateUpdatePermission(Set<String> actorRoles, Set<String> targetRoles) {
    if (targetRoles.contains(SUPERADMIN_ROLE)) {
      throw new ForbiddenOperationException("SUPERADMIN users cannot be updated in this block");
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
    throw new ForbiddenOperationException("Actor cannot update the target user");
  }

  private void validateSelfDeactivation(UpdateCommand command, User target) {
    if (Boolean.TRUE.equals(command.active())) {
      return;
    }
    String actorUserId = command.actorUserId();
    if (actorUserId != null && actorUserId.equalsIgnoreCase(target.id().toString())) {
      throw new ForbiddenOperationException("Users cannot deactivate themselves");
    }
  }

  private Set<String> targetRoleNames(User user) {
    return user.roles().stream().map(Role::name).collect(Collectors.toUnmodifiableSet());
  }

  private UserSummary toSummary(User user) {
    return new UserSummary(
        user.id().toString(),
        user.email().value(),
        user.isActive(),
        user.isVerified(),
        user.roles().stream().map(Role::name).collect(Collectors.toUnmodifiableSet()),
        user.lastLogin(),
        user.createdAt(),
        user.updatedAt(),
        user.deletedAt());
  }
}
