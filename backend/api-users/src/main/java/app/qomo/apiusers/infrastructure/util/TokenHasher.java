package app.qomo.apiusers.infrastructure.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes short-lived verification material before it reaches persistence or comparison logic.
 *
 * <p>The users module wires this helper into the email-verification issuer and verifier so raw OTP
 * values are not stored in the verification-token repository. Input is expected to be the exact
 * token string chosen by the caller; this helper does not trim, case-fold, or validate token shape.
 *
 * <p>The output is the deterministic lowercase hexadecimal representation of the SHA-256 digest of
 * the UTF-8 input bytes. Empty strings are hashable because SHA-256 defines that case, while null
 * values are outside the supported input contract. This class does not add salt or pepper, perform
 * password hashing, compare hashes, or make decisions about token expiry and attempts.
 */
public class TokenHasher {

  /**
   * Produces a stable SHA-256 digest for token storage and later equality checks.
   *
   * @param input non-null token text whose exact UTF-8 bytes should be hashed
   * @return a 64-character lowercase hexadecimal SHA-256 digest
   * @throws IllegalStateException if the JVM does not provide SHA-256
   */
  public String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(hash.length * 2);
      for (byte value : hash) {
        hex.append(String.format("%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 algorithm is not available", ex);
    }
  }
}
