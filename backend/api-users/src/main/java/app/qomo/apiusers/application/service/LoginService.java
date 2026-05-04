package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.exception.InvalidCredentialsException;
import app.qomo.apiusers.application.exception.UserInactiveException;
import app.qomo.apiusers.application.port.in.LoginUseCase;
import app.qomo.apiusers.application.port.out.ClockPort;
import app.qomo.apiusers.application.port.out.PasswordVerifierPort;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import java.util.Objects;

public class LoginService implements LoginUseCase {

  private final UserRepositoryPort userRepository;
  private final PasswordVerifierPort passwordVerifier;
  private final ClockPort clock;
  private final IssueEmailVerificationService issueEmailVerificationService;

  public LoginService(
      UserRepositoryPort userRepository,
      PasswordVerifierPort passwordVerifier,
      ClockPort clock,
      IssueEmailVerificationService issueEmailVerificationService) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    this.passwordVerifier =
        Objects.requireNonNull(passwordVerifier, "passwordVerifier cannot be null");
    this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    this.issueEmailVerificationService =
        Objects.requireNonNull(
            issueEmailVerificationService, "issueEmailVerificationService cannot be null");
  }

  @Override
  public Result login(Command command) {
    if (command == null) {
      throw InvalidCommandException.missing("command");
    }
    if (command.email() == null) {
      throw InvalidCommandException.missing("email");
    }
    if (command.password() == null) {
      throw InvalidCommandException.missing("password");
    }

    var user =
        userRepository
            .findByEmail(new Email(command.email()).value())
            .orElseThrow(InvalidCredentialsException::new);

    if (!passwordVerifier.matches(command.password(), user.passwordHash())) {
      throw new InvalidCredentialsException();
    }

    if (!user.isActive()) {
      throw new UserInactiveException();
    }

    if (!user.isVerified()) {
      var correlationId = java.util.UUID.randomUUID().toString();
      var issueResult =
          issueEmailVerificationService.issue(
              user.id(), user.email(), "LOGIN_UNVERIFIED", correlationId);
      return new Result(null, true, issueResult.verificationSessionId(), issueResult.ttlSeconds());
    }

    var now = clock.now();
    user.recordLogin(now);
    var saved = userRepository.save(user);

    return new Result(saved, false, null, 0);
  }
}
