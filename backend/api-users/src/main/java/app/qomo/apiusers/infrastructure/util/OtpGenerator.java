package app.qomo.apiusers.infrastructure.util;

import java.security.SecureRandom;

/**
 * Generates user-facing email-verification codes with a cryptographically strong random source.
 *
 * <p>The users module wires this helper into the email-verification issuer, which is responsible
 * for hashing, persistence, delivery, expiry, resend throttling, and attempt limits. This class
 * only creates the transient numeric code that can be sent to the user.
 *
 * <p>Generated values are formatted as exactly six ASCII decimal digits, including leading zeroes.
 * The full generated range is {@code 000000} through {@code 999999}. Uniqueness, rate limiting, and
 * replay protection are intentionally handled by the verification-token workflow rather than by
 * this generator.
 */
public class OtpGenerator {

  private static final int OTP_MAX_EXCLUSIVE = 1_000_000;

  private final SecureRandom secureRandom;

  /** Creates a generator backed by the JVM's configured {@link SecureRandom} provider. */
  public OtpGenerator() {
    this.secureRandom = new SecureRandom();
  }

  /**
   * Generates one numeric verification code and preserves leading zeroes in the returned text.
   *
   * @return a six-character string containing only decimal digits
   */
  public String generate6Digits() {
    int value = secureRandom.nextInt(OTP_MAX_EXCLUSIVE);
    return "%06d".formatted(value);
  }
}
