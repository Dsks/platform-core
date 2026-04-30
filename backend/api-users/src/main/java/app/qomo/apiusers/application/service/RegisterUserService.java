package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.RoleNotFoundException;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import app.qomo.apiusers.domain.port.out.ClockPort;
import app.qomo.apiusers.domain.port.out.PasswordHasherPort;
import app.qomo.apiusers.domain.port.out.RoleRepositoryPort;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class RegisterUserService implements RegisterUserUseCase {

  private static final String DEFAULT_ROLE = "USER";
  private static final String AGGREGATE_TYPE = "auth_user";

  private final UserRepositoryPort userRepository;
  private final RoleRepositoryPort roleRepository;
  private final PasswordHasherPort passwordHasher;
  private final ClockPort clock;
  private final IssueEmailVerificationService issueEmailVerificationService;

  public RegisterUserService(
      UserRepositoryPort userRepository,
      RoleRepositoryPort roleRepository,
      PasswordHasherPort passwordHasher,
      ClockPort clock,
      IssueEmailVerificationService issueEmailVerificationService) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository cannot be null");
    this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    this.issueEmailVerificationService = Objects.requireNonNull(
        issueEmailVerificationService,
        "issueEmailVerificationService cannot be null");
  }

  @Override
  @Transactional
  public Result register(Command command) {
    if (command == null) {
      throw InvalidCommandException.missing("command");
    }
    if (command.email() == null) {
      throw InvalidCommandException.missing("email");
    }
    if (command.rawPassword() == null) {
      throw InvalidCommandException.missing("rawPassword");
    }
    if (command.rawPassword().isBlank()) {
      throw InvalidCommandException.blank("rawPassword");
    }

    final Email email;
    try {
      email = Email.of(command.email());
    } catch (IllegalArgumentException ex) {
      throw new InvalidCommandException("email", "invalid");
    }

    var requestId = UUID.randomUUID().toString();

    var existing = userRepository.findByEmail(email.value());
    if (existing.isPresent()) {
      if (!existing.get().isVerified()) {
        var issueResult = issueEmailVerificationService.issue(
            existing.get().id(),
            email,
            "REGISTER_EXISTING_UNVERIFIED",
            requestId);
        return new Result(requestId, issueResult.verificationSessionId(), issueResult.ttlSeconds());
      }
      return new Result(requestId, null, 0);
    }

    var now = clock.now();
    var id = UserId.newId();
    var hash = passwordHasher.hash(command.rawPassword());
    var user = User.createNew(id, email, hash, now);

    var defaultRole =
        roleRepository
            .findByName(DEFAULT_ROLE)
            .orElseThrow(() -> new RoleNotFoundException(DEFAULT_ROLE));

    user.addRole(defaultRole, now);
    userRepository.save(user);

    var issueResult = issueEmailVerificationService.issue(id, email, "REGISTER_NEW", requestId);
    return new Result(requestId, issueResult.verificationSessionId(), issueResult.ttlSeconds());
  }
}
