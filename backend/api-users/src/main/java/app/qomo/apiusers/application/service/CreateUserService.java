package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.exception.EmailAlreadyInUseException;
import app.qomo.apiusers.application.exception.ForbiddenOperationException;
import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.RoleNotFoundException;
import app.qomo.apiusers.application.port.in.CreateUserUseCase;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.CurrentUserPort;
import app.qomo.apiusers.application.port.out.PasswordHasherPort;
import app.qomo.apiusers.application.port.out.RoleRepositoryPort;
import app.qomo.apiusers.application.port.out.UserEventPublisherPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.Role;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Orchestrates administrative user creation.
 *
 * <p>The service coordinates the current-user context, role repository, user repository, password
 * hasher, event publisher, and clock. It protects administrative invariants around email
 * uniqueness, role assignment policy, and password handling: plaintext credentials are hashed
 * before persistence, only allowed target roles can be assigned, and creating an ADMIN account
 * requires the current actor to hold SUPERADMIN.
 *
 * <p>This flow creates the account directly and publishes the user-created event; it does not issue
 * email-verification challenges. Raw passwords, password hashes, and personal data should not be
 * logged in clear text by callers or adapters.
 */
public class CreateUserService implements CreateUserUseCase {

  private static final String DEFAULT_ROLE = "USER";
  private static final String ADMIN_ROLE = "ADMIN";
  private static final String SUPERADMIN_ROLE = "SUPERADMIN";
  private static final Set<String> ALLOWED_TARGET_ROLES = Set.of(DEFAULT_ROLE, ADMIN_ROLE);

  private final UserRepositoryPort userRepository;
  private final RoleRepositoryPort roleRepository;
  private final PasswordHasherPort passwordHasher;
  private final UserEventPublisherPort eventPublisher;
  private final ClockPort clock;
  private final CurrentUserPort currentUser;

  public CreateUserService(
      UserRepositoryPort userRepository,
      RoleRepositoryPort roleRepository,
      PasswordHasherPort passwordHasher,
      UserEventPublisherPort eventPublisher,
      ClockPort clock,
      CurrentUserPort currentUser) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository cannot be null");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher cannot be null");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    this.currentUser = Objects.requireNonNull(currentUser, "currentUser cannot be null");
  }

  /**
   * Creates a user from an administrative entry point and assigns the requested roles after
   * applying role policy.
   *
   * <p>Missing command, email, or raw password values are rejected before business processing.
   * Duplicate emails fail explicitly because this administrative flow is allowed to expose that
   * conflict. Null, blank, or absent role requests are normalized to the default USER role before
   * permission checks and role lookup. A successful call persists the user and publishes the
   * user-created event.
   *
   * @param command administrative creation command containing email, plaintext password, and
   *     optional roles
   * @return the id of the persisted user
   * @throws InvalidCommandException when the command, email, or raw password is missing
   * @throws EmailAlreadyInUseException when the canonical email is already assigned to a user
   * @throws ForbiddenOperationException when the requested roles are outside the allowed target set
   *     or the current actor may not create an ADMIN user
   * @throws RoleNotFoundException when an allowed requested role cannot be resolved
   */
  @Override
  public Result create(Command command) {
    if (command == null) {
      throw InvalidCommandException.missing("command");
    }
    if (command.email() == null) {
      throw InvalidCommandException.missing("email");
    }
    if (command.rawPassword() == null) {
      throw InvalidCommandException.missing("rawPassword");
    }

    var email = new Email(command.email());

    if (userRepository.existsByEmail(email.value())) {
      // Administrative creation may expose duplicate-email conflicts unlike public registration.
      throw new EmailAlreadyInUseException(email.value());
    }

    Set<String> requestedRoles = normalizeRoles(command.roles());
    // Apply permission policy only after role names are canonicalized.
    validateRolePermissions(requestedRoles);

    var now = clock.now();
    var id = UserId.newId();
    var hash = passwordHasher.hash(command.rawPassword());

    var user = User.createNew(id, email, hash, now);

    for (String roleName : requestedRoles) {
      Role role =
          roleRepository
              .findByName(roleName)
              .orElseThrow(() -> new RoleNotFoundException(roleName));
      user.addRole(role, now);
    }

    var saved = userRepository.save(user);
    // Publish after persistence so consumers observe a durable user id.
    eventPublisher.userCreated(saved);

    return new Result(saved.id());
  }

  /**
   * Converts caller-provided role names into the canonical policy vocabulary.
   *
   * <p>Blank entries are ignored and an empty effective set falls back to USER so role assignment
   * is deterministic even when adapters omit optional role data.
   */
  private Set<String> normalizeRoles(Set<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return Set.of(DEFAULT_ROLE);
    }

    Set<String> normalized = new LinkedHashSet<>();
    for (String role : roles) {
      if (role == null || role.isBlank()) {
        continue;
      }
      normalized.add(role.trim().toUpperCase(Locale.ROOT));
    }

    if (normalized.isEmpty()) {
      return Set.of(DEFAULT_ROLE);
    }
    return Set.copyOf(normalized);
  }

  /**
   * Enforces role-escalation rules for administrative creation.
   *
   * <p>The current policy allows creating USER and ADMIN accounts only, and ADMIN creation requires
   * a SUPERADMIN actor in the current-user port.
   */
  private void validateRolePermissions(Set<String> requestedRoles) {
    if (!ALLOWED_TARGET_ROLES.containsAll(requestedRoles)) {
      throw new ForbiddenOperationException(
          "Target role is not allowed for administrative creation");
    }

    if (requestedRoles.contains(ADMIN_ROLE)
        && !currentUser.roles().stream()
            .map(role -> role.toUpperCase(Locale.ROOT))
            .anyMatch(SUPERADMIN_ROLE::equals)) {
      throw new ForbiddenOperationException("Only SUPERADMIN can create ADMIN users");
    }
  }
}
