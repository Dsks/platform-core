package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.RoleNotFoundException;
import app.qomo.apiusers.application.port.in.RegisterUserUseCase;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.PasswordHasherPort;
import app.qomo.apiusers.application.port.out.RoleRepositoryPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.model.User;
import app.qomo.apiusers.domain.model.UserId;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates public account registration and the first email-verification challenge.
 *
 * <p>The service coordinates user and role repositories, password hashing, the application clock,
 * and email-verification issuance. It protects the registration invariants that emails are
 * canonicalized before lookup, new accounts start with the configured default role, and plaintext
 * passwords are converted to hashes before a user is persisted.
 *
 * <p>Registration intentionally avoids confirming whether a verified account already exists: an
 * existing verified email receives an accepted result without a verification session. Existing
 * unverified users are routed back into verification issuance. Raw passwords, OTPs, verification
 * sessions, and other secrets must not be logged in clear text by callers or adapters.
 */
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
    this.issueEmailVerificationService =
        Objects.requireNonNull(
            issueEmailVerificationService, "issueEmailVerificationService cannot be null");
  }

  /**
   * Accepts a registration request and starts email verification when the application can act on
   * the email.
   *
   * <p>For a new email, the method creates an active but unverified user, hashes the supplied
   * password, assigns the default role, persists the aggregate, and issues an email-verification
   * OTP. For an existing unverified user, it issues or rate-limits a new verification challenge
   * without changing the password. For an existing verified user, it returns an accepted result
   * without a session so the caller can preserve anti-enumeration behavior.
   *
   * @param command registration data containing email and plaintext password material
   * @return a request correlation id and, when issued, the verification session id and TTL
   * @throws InvalidCommandException when the command, email, or password is missing, when the
   *     password is blank, or when the email is structurally invalid
   * @throws RoleNotFoundException when the configured default role cannot be resolved
   */
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
        var issueResult =
            issueEmailVerificationService.issue(
                existing.get().id(), email, "REGISTER_EXISTING_UNVERIFIED", requestId);
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
