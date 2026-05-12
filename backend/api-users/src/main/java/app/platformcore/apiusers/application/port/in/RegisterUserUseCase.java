package app.platformcore.apiusers.application.port.in;

import app.platformcore.apiusers.application.exception.InvalidCommandException;
import app.platformcore.apiusers.application.exception.RoleNotFoundException;
import java.util.UUID;

/**
 * Registers an end-user account and starts email verification when appropriate.
 *
 * <p>This port is intended for public registration adapters such as web controllers and for
 * application tests. Implementations are responsible for normalizing and validating the email,
 * hashing the password, assigning the default role, creating the user when needed, and issuing the
 * verification session. HTTP authentication, JSON parsing, cookie handling, response wording, and
 * concrete persistence technology are outside this contract.
 *
 * <p>The registration contract avoids exposing whether a verification-required response belongs to
 * a new account or to an existing unverified account. Existing verified accounts are reported
 * explicitly so browser clients can guide legitimate users back to login. Callers must not log
 * plaintext credentials or verification-session values.
 */
public interface RegisterUserUseCase {

  /**
   * Captures the user-provided registration data.
   *
   * @param email personal data used as the account identifier; adapters should redact it in logs
   *     unless a controlled diagnostic path requires it
   * @param rawPassword plaintext credential material to be hashed by the implementation; never log,
   *     echo, or persist it as-is
   */
  record Command(String email, String rawPassword) {}

  /**
   * Stable registration outcome understood by inbound adapters.
   *
   * <p>{@link RegistrationStatus#VERIFICATION_REQUIRED} intentionally covers both newly created
   * accounts and existing unverified accounts. {@link RegistrationStatus#ALREADY_REGISTERED} is
   * only used when an existing account has already completed verification.
   */
  enum RegistrationStatus {
    VERIFICATION_REQUIRED,
    ALREADY_REGISTERED
  }

  /**
   * Carries the registration outcome without revealing new-vs-unverified account existence.
   *
   * @param requestId correlation identifier for the registration attempt; not a credential, but it
   *     should still be handled as operational trace data
   * @param status outcome category for the HTTP adapter
   * @param verificationSessionId verification session for new or existing unverified accounts;
   *     {@code null} when no session should be exposed to the adapter
   * @param verificationTtlSeconds lifetime of the verification session in seconds; {@code 0} when
   *     no session was issued
   */
  record Result(
      String requestId,
      RegistrationStatus status,
      UUID verificationSessionId,
      long verificationTtlSeconds) {}

  /**
   * Accepts a registration request and starts the email-verification flow when the application can
   * act on it.
   *
   * <p>Implementations should keep account creation and verification-session issuance within the
   * same application transaction when a new account is created. Existing unverified accounts should
   * re-enter the verification-required flow without creating a duplicate user. Existing verified
   * accounts should return {@link RegistrationStatus#ALREADY_REGISTERED} so the adapter can steer
   * users to login.
   *
   * @param command registration data supplied by an inbound adapter; command, email, and raw
   *     password must be present, and the password must not be blank
   * @return an accepted registration result with a request identifier and, only when appropriate, a
   *     verification session
   * @throws InvalidCommandException when required command data is missing, blank, or invalid
   * @throws RoleNotFoundException when the configured default role cannot be resolved
   */
  Result register(Command command);
}
