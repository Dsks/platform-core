package app.qomo.apiusers.application.observability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Centralizes small privacy-preserving helpers used by the application layer when it needs
 * operational observability for user data. Callers may log coarse outcomes, counters, and derived
 * identifiers produced by this class; they must not log raw email addresses, credentials,
 * verification tokens, authorization headers, or other externally supplied PII in clear text.
 *
 * <p>The fingerprints produced here are intended for short operational correlation, for example to
 * connect several log events that refer to the same email without exposing the email itself. They
 * are not secrets, authenticators, or a guarantee of absolute anonymization: the current algorithm
 * is deterministic, unsalted, and truncated, so values from small or guessable input spaces could
 * still be tested offline by someone with access to the logs.
 */
public final class PiiUtil {

  private PiiUtil() {}

  /**
   * Builds a deterministic email fingerprint for logs and metrics where the original email address
   * would be too sensitive to record.
   *
   * <p>The input is trimmed and lower-cased before hashing with SHA-256, and the returned value is
   * the first 12 hexadecimal characters of that digest. The result is safer to log than the raw
   * email and is useful for correlating operational events, but it must not be treated as an
   * authentication factor, a secret, or irreversible anonymized data.
   *
   * <p>Use this method instead of logging the email itself when investigating duplicate email,
   * verification, or account lifecycle flows. {@code null} input returns {@code null}. Blank input
   * is not rejected; after normalization it produces the fingerprint of the empty string. This
   * method does not validate email syntax. If the hashing provider fails unexpectedly, the safe
   * sentinel {@code hash_error} is returned.
   *
   * @param email email address to correlate without writing the clear value to observability sinks
   * @return a log-oriented deterministic fingerprint, {@code null}, or {@code hash_error}
   */
  public static String emailFingerprint(String email) {
    if (email == null) {
      return null;
    }
    var normalized = email.trim().toLowerCase();
    try {
      var md = MessageDigest.getInstance("SHA-256");
      var bytes = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes).substring(0, 12);
    } catch (Exception e) {
      return "hash_error";
    }
  }
}
