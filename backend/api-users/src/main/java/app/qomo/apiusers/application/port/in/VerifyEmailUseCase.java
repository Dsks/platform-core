package app.qomo.apiusers.application.port.in;

import java.util.UUID;

/**
 * Completes email verification using a verification session and one-time code.
 *
 * <p>This port is intended for inbound adapters such as verification controllers and application
 * tests. Implementations are responsible for locating the active verification token, comparing the
 * submitted code securely, recording failed attempts when applicable, marking the user as verified,
 * and consuming the token. Cookie extraction, UUID parsing from transport values, HTTP response
 * shaping, and concrete token storage are outside this contract.
 *
 * <p>The boolean result is deliberately generic: callers should not expose whether the session,
 * code, token expiry, or attempt limit caused verification to fail. The code and session identifier
 * should be treated as sensitive verification data and must not be logged in clear text.
 */
public interface VerifyEmailUseCase {

  /**
   * Carries the verification material submitted by the user.
   *
   * @param sessionId verification session identifier obtained through a protected adapter channel;
   *     treat it as sensitive token-like data
   * @param code one-time verification code; credential-like secret that must never be logged or
   *     echoed
   */
  record Command(UUID sessionId, String code) {}

  /**
   * Attempts to verify the user's email for the supplied session.
   *
   * <p>Implementations should perform successful verification and token consumption in the same
   * application transaction. A {@code false} result covers malformed commands, missing or expired
   * sessions, exceeded attempts, and incorrect codes without disclosing which condition occurred.
   *
   * @param command verification data supplied by an inbound adapter; session id and six-digit code
   *     must be present
   * @return {@code true} only when the email was verified and the token was consumed; {@code false}
   *     for all non-success verification outcomes
   */
  boolean verify(Command command);
}
