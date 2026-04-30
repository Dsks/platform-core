package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.qomo.apiusers.domain.model.Email;
import app.qomo.apiusers.domain.port.out.UserRepositoryPort;
import java.util.Objects;

public class ResendEmailVerificationService implements ResendEmailVerificationUseCase {

  private final UserRepositoryPort userRepository;
  private final IssueEmailVerificationService issueEmailVerificationService;

  public ResendEmailVerificationService(
      UserRepositoryPort userRepository,
      IssueEmailVerificationService issueEmailVerificationService) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    this.issueEmailVerificationService = Objects.requireNonNull(
        issueEmailVerificationService,
        "issueEmailVerificationService cannot be null");
  }

  @Override
  public Result resend(Command command) {
    if (command == null || command.email() == null) {
      throw InvalidCommandException.missing("email");
    }

    final Email email;
    try {
      email = Email.of(command.email());
    } catch (IllegalArgumentException ex) {
      return new Result(null, 0);
    }

    var user = userRepository.findByEmail(email.value());
    if (user.isEmpty() || user.get().isVerified()) {
      return new Result(null, 0);
    }

    var issueResult = issueEmailVerificationService.issue(
        user.get().id(),
        email,
        "RESEND_ENDPOINT",
        java.util.UUID.randomUUID().toString());

    return new Result(issueResult.verificationSessionId(), issueResult.ttlSeconds());
  }
}