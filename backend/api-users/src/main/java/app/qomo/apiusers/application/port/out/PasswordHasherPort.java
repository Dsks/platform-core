package app.qomo.apiusers.application.port.out;

import app.qomo.apiusers.domain.model.PasswordHash;

/**
 * Abstracts creation of password hashes before user credentials are persisted.
 *
 * <p>The application expects implementations to return a hash representation that is safe to store
 * and compatible with {@link PasswordVerifierPort}. Raw passwords are credentials and must never be
 * logged, included in errors, or retained longer than necessary.
 *
 * <p>Password strength policy and user-facing validation belong to application use cases, not to
 * this hashing contract.
 */
public interface PasswordHasherPort {

  /**
   * Converts a raw password into a persisted password hash representation.
   *
   * @param rawPassword credential supplied by the caller
   * @return hash value to store with the user aggregate
   * @throws IllegalArgumentException when the implementation rejects an unsupported raw password
   *     value
   */
  PasswordHash hash(String rawPassword);
}
