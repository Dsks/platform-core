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

/**
 * Orchestrates password-based authentication for user accounts.
 *
 * <p>The service coordinates user lookup, password-hash verification, account lifecycle checks,
 * login timestamp persistence, and email-verification issuance for valid but unverified accounts.
 * It deliberately returns generic credential failures for unknown emails and password mismatches so
 * callers do not learn which credential component failed.
 *
 * <p>JWT generation and cookie handling are outside this service: a successful verified login
 * returns the domain user for the adapter or token layer to continue the session flow. Plaintext
 * passwords, password hashes, OTPs, verification-session identifiers, and token material must not
 * be logged in clear text.
 */
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

  /**
   * Authenticates a user by email and password, or returns an email-verification challenge state
   * when credentials are valid for an unverified account.
   *
   * <p>Unknown emails and invalid passwords both raise {@link InvalidCredentialsException}.
   * Inactive accounts are rejected after credentials match. Successful verified logins update the
   * user's last-login timestamp and persist the aggregate; unverified logins issue or rate-limit a
   * verification challenge and do not record a completed login.
   *
   * @param command credentials submitted by the caller
   * @return the authenticated user for verified accounts, or a verification-required result with
   *     session metadata for valid unverified accounts
   * @throws InvalidCommandException when the command, email, or password is missing
   * @throws InvalidCredentialsException when the email is unknown or the password does not match
   *     the stored hash
   * @throws UserInactiveException when the account exists, credentials match, and the account is
   *     inactive
   */
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
            // Use the domain email value so credential checks run against the canonical lookup key.
            .findByEmail(new Email(command.email()).value())
            .orElseThrow(InvalidCredentialsException::new);

    if (!passwordVerifier.matches(command.password(), user.passwordHash())) {
      // Unknown emails and password mismatches share the same exception boundary.
      throw new InvalidCredentialsException();
    }

    if (!user.isActive()) {
      throw new UserInactiveException();
    }

    if (!user.isVerified()) {
      // Valid credentials for unverified accounts trigger verification, not a completed login.
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
