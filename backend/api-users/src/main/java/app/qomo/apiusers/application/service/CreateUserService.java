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
      throw new EmailAlreadyInUseException(email.value());
    }

    Set<String> requestedRoles = normalizeRoles(command.roles());
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
    eventPublisher.userCreated(saved);

    return new Result(saved.id());
  }

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
