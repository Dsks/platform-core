package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.PasswordHash;

/**
 * Abstracts password verification against stored password hashes.
 *
 * <p>The application expects implementations to delegate to a secure password verification
 * mechanism suitable for the hash format in use. Raw passwords and hashes are credentials and must
 * never be written to logs, metrics, or exception messages.
 *
 * <p>This contract does not choose password policy, account lockout rules, or authentication error
 * responses.
 */
public interface PasswordVerifierPort {

  /**
   * Compares a raw password supplied by a caller with a stored hash.
   *
   * @param rawPassword credential presented for authentication
   * @param passwordHash stored password hash to verify against
   * @return {@code true} only when the raw password matches the stored hash
   */
  boolean matches(String rawPassword, PasswordHash passwordHash);
}
