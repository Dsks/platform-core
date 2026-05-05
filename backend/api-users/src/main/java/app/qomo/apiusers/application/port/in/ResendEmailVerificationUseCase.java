package app.qomo.apiusers.application.port.in;

import java.util.UUID;

/**
 * Requests a new email-verification session for an unverified account.
 *
 * <p>This port is intended for inbound adapters such as verification controllers, recovery jobs, or
 * application tests. Implementations are responsible for validating the email, deciding whether an
 * unverified user can receive another verification session, and issuing that session. Transport
 * parsing, HTTP response wording, cookie handling, rate limiting, and concrete persistence
 * mechanisms stay outside this contract.
 *
 * <p>The contract intentionally supports generic outcomes so callers can avoid revealing whether an
 * email exists, is invalid, or is already verified. Adapters must not log email addresses or issued
 * verification-session identifiers in clear text.
 */
public interface ResendEmailVerificationUseCase {

  /**
   * Identifies the account that is requesting another verification challenge.
   *
   * @param email personal data submitted by the caller; treat it as sensitive in logs and
   *     diagnostics
   */
  record Command(String email) {}

  /**
   * Represents the optional verification session produced by a resend request.
   *
   * @param verificationSessionId verification session to be delivered through a protected channel;
   *     {@code null} when the caller should receive only the generic accepted response
   * @param verificationTtlSeconds lifetime of the issued session in seconds; {@code 0} when no
   *     session was issued
   */
  record Result(UUID verificationSessionId, long verificationTtlSeconds) {}

  /**
   * Issues a new verification session only when the supplied email belongs to an unverified user.
   *
   * <p>Invalid email syntax, unknown emails, and already verified accounts are represented as a
   * result without a session so inbound adapters can preserve anti-enumeration behavior.
   *
   * @param command resend data supplied by an inbound adapter; command and email must be present
   * @return a session-bearing result when a challenge was issued, otherwise a generic no-session
   *     result
   * @throws app.qomo.apiusers.application.exception.InvalidCommandException when the command or
   *     email is missing
   */
  Result resend(Command command);
}
