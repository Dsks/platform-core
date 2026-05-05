package app.qomo.apiusers.application.service;

import app.qomo.apiusers.application.exception.InvalidCommandException;
import app.qomo.apiusers.application.port.in.ResendEmailVerificationUseCase;
import app.qomo.apiusers.application.port.out.UserRepositoryPort;
import app.qomo.apiusers.domain.model.Email;
import java.util.Objects;

/**
 * Orchestrates resend requests for email verification without revealing account existence.
 *
 * <p>The service validates the inbound email value, reads the user aggregate through the user
 * repository, and delegates actual token issuance to {@link IssueEmailVerificationService}. It only
 * issues a new verification session for an existing unverified user; malformed emails, unknown
 * users, and already verified users are represented as generic no-session outcomes.
 *
 * <p>This distinction keeps "resend" separate from initial issuance: resend is an externally
 * requested retry for an unverified account, while issuance owns token rotation, rate limiting, and
 * outbox publication. Email addresses and verification-session identifiers should not be logged in
 * clear text by callers.
 */
public class ResendEmailVerificationService implements ResendEmailVerificationUseCase {

  private final UserRepositoryPort userRepository;
  private final IssueEmailVerificationService issueEmailVerificationService;

  public ResendEmailVerificationService(
      UserRepositoryPort userRepository,
      IssueEmailVerificationService issueEmailVerificationService) {
    this.userRepository = Objects.requireNonNull(userRepository, "userRepository cannot be null");
    this.issueEmailVerificationService =
        Objects.requireNonNull(
            issueEmailVerificationService, "issueEmailVerificationService cannot be null");
  }

  /**
   * Handles a request to send another email-verification challenge.
   *
   * <p>A missing command or email is rejected as invalid input. Invalid email syntax, absence of a
   * matching account, and already verified users return {@code (null, 0)} so inbound adapters can
   * answer generically. When a resend is allowed, the operation may invalidate previous active
   * email-verification tokens, create a new token, and enqueue an email command through the
   * issuance service.
   *
   * @param command email submitted by the caller for another verification challenge
   * @return a verification session id and TTL when a challenge was issued, otherwise a generic
   *     no-session result
   * @throws InvalidCommandException when the command or email field is missing
   */
  @Override
  public Result resend(Command command) {
    if (command == null || command.email() == null) {
      throw InvalidCommandException.missing("email");
    }

    final Email email;
    try {
      email = Email.of(command.email());
    } catch (IllegalArgumentException ex) {
      // Invalid syntax is treated as a no-session outcome to preserve resend anti-enumeration.
      return new Result(null, 0);
    }

    var user = userRepository.findByEmail(email.value());
    if (user.isEmpty() || user.get().isVerified()) {
      // Unknown and already verified accounts collapse to the same externally generic result.
      return new Result(null, 0);
    }

    // Issuance owns token rotation and resend throttling; this service only gates eligibility.
    var issueResult =
        issueEmailVerificationService.issue(
            user.get().id(), email, "RESEND_ENDPOINT", java.util.UUID.randomUUID().toString());

    return new Result(issueResult.verificationSessionId(), issueResult.ttlSeconds());
  }
}
