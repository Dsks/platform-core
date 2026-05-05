package app.qomo.emailsender.application.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Provides privacy-aware representations of email-related values for operational observability.
 *
 * <p>The fingerprint produced here is intended to let logs, metrics, or persisted diagnostic
 * records correlate events that refer to the same email address without writing the original
 * address. It protects the raw email value by trimming and lowercasing it before hashing, then
 * keeping only a short, deterministic prefix of the digest.
 *
 * <p>Consumers should treat the result as a diagnostic fingerprint, not as a business identifier or
 * proof of anonymity. The method does not validate email syntax, does not encrypt the input, and
 * does not provide broader anonymization guarantees beyond avoiding direct exposure of the original
 * value.
 */
public final class PiiUtil {

  private PiiUtil() {}

  /**
   * Builds a stable, log-safe fingerprint for an email-like value.
   *
   * <p>Inputs are normalized with {@link String#trim()} and {@link String#toLowerCase()} before
   * hashing so that casing and surrounding whitespace do not create separate diagnostic keys. A
   * {@code null} input returns {@code null}; an input that becomes blank after trimming returns an
   * empty string. If the hashing operation cannot be completed, the method returns a fixed
   * diagnostic marker instead of the original value.
   *
   * @param email raw email-like value that must not be written directly to logs
   * @return a deterministic fingerprint for correlation, or the documented empty/fallback values
   */
  public static String emailFingerprint(String email) {
    if (email == null) {
      return null;
    }

    String normalized = email.trim().toLowerCase();
    if (normalized.isBlank()) {
      return "";
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash).substring(0, 12);
    } catch (Exception ex) {
      return "hash_error";
    }
  }
}
