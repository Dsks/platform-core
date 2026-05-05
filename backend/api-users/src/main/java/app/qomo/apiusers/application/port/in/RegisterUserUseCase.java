package app.qomo.apiusers.application.port.in;

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
 * <p>The registration contract is designed to avoid exposing whether an email already belongs to a
 * verified account. Callers should keep client responses generic and must not log plaintext
 * credentials or verification-session values.
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
   * Carries the accepted registration outcome without revealing account existence.
   *
   * @param requestId correlation identifier for the registration attempt; not a credential, but it
   *     should still be handled as operational trace data
   * @param verificationSessionId verification session for new or existing unverified accounts;
   *     {@code null} when no session should be exposed to the adapter
   * @param verificationTtlSeconds lifetime of the verification session in seconds; {@code 0} when
   *     no session was issued
   */
  record Result(String requestId, UUID verificationSessionId, long verificationTtlSeconds) {}

  /**
   * Accepts a registration request and starts the email-verification flow when the application can
   * act on it.
   *
   * <p>Implementations should keep account creation and verification-session issuance within the
   * same application transaction when a new account is created. Existing verified accounts are
   * represented by an accepted result without a verification session so the adapter can preserve
   * anti-enumeration behavior.
   *
   * @param command registration data supplied by an inbound adapter; command, email, and raw
   *     password must be present, and the password must not be blank
   * @return an accepted registration result with a request identifier and, only when appropriate, a
   *     verification session
   * @throws app.qomo.apiusers.application.exception.InvalidCommandException when required command
   *     data is missing, blank, or invalid
   * @throws app.qomo.apiusers.application.exception.RoleNotFoundException when the configured
   *     default role cannot be resolved
   */
  Result register(Command command);
}
