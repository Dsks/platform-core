package app.platformcore.apiusers.application.port.in;

import app.platformcore.apiusers.application.exception.InvalidCommandException;
import app.platformcore.apiusers.application.exception.InvalidCredentialsException;
import app.platformcore.apiusers.application.exception.UserInactiveException;
import app.platformcore.apiusers.domain.model.User;
import java.util.UUID;

/**
 * Authenticates a user with email and password credentials.
 *
 * <p>This port is intended for inbound adapters such as authentication controllers or application
 * tests. Implementations are responsible for credential verification, account-state checks,
 * recording successful logins, and issuing an email-verification session when a valid but
 * unverified account attempts to log in. HTTP concerns such as cookie creation, JWT creation,
 * request parsing, and status-code mapping stay outside this contract.
 *
 * <p>The command contains credential material. Inbound adapters must not log the password, and they
 * should treat email addresses and verification session identifiers as sensitive or personal data.
 */
public interface LoginUseCase {

  /**
   * Holds the credentials submitted for an authentication attempt.
   *
   * @param email personal data used to locate the user account; avoid logging it in clear text in
   *     authentication flows
   * @param password plaintext credential material that must never be logged, echoed, or persisted
   */
  record Command(String email, String password) {}

  /**
   * Describes the business outcome of a credential check.
   *
   * @param user authenticated user when login completed; absent when the account still needs email
   *     verification
   * @param emailNotVerified {@code true} when credentials were valid but login cannot complete
   *     until email verification succeeds
   * @param verificationSessionId verification session to be delivered through a protected channel;
   *     treat it as sensitive and do not log it in clear text
   * @param verificationTtlSeconds lifetime of the verification session in seconds; {@code 0} when
   *     no verification session was issued
   */
  record Result(
      User user,
      boolean emailNotVerified,
      UUID verificationSessionId,
      long verificationTtlSeconds) {}

  /**
   * Verifies credentials and returns either an authenticated user or an email-verification
   * challenge state.
   *
   * <p>Invalid credentials are reported generically so callers do not learn whether the email or
   * password was the failing part.
   *
   * @param command credentials supplied by an inbound adapter; command, email, and password must be
   *     present
   * @return a completed login result, or a verification-required result for a valid unverified
   *     account
   * @throws InvalidCommandException when required command data is missing
   * @throws InvalidCredentialsException when credentials are invalid without disclosing which value
   *     failed
   * @throws UserInactiveException when the account exists but is not active
   */
  Result login(Command command);
}
