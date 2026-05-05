package app.qomo.emailsender.application.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Creates deterministic hash strings for observability workflows that need to compare or correlate
 * values without storing the original input in logs or error records.
 *
 * <p>The helper protects the direct value by returning a SHA-256 hex digest of the exact string it
 * receives. It does not normalize, redact, validate, salt, or encrypt the input, so callers must
 * not assume that the output is suitable as a business identifier or that it provides anonymity for
 * low-entropy values.
 */
@Component
public class HashUtil {

  /**
   * Hashes the provided value as UTF-8 and returns the full lowercase hexadecimal digest.
   *
   * <p>The method is appropriate for diagnostic correlation where writing the raw value would
   * expose sensitive content. A {@code null} input returns an empty string. Non-null inputs are
   * hashed exactly as provided, including surrounding whitespace and casing.
   *
   * @param value raw value that should not be exposed directly in observability output
   * @return full SHA-256 hexadecimal digest, or an empty string for {@code null}
   * @throws IllegalStateException if the runtime does not provide SHA-256
   */
  public String sha256Hex(String value) {
    if (value == null) {
      return "";
    }

    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 algorithm is not available", ex);
    }
  }
}
