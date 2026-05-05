package app.qomo.apiusers.infrastructure.adapter.out.crypto;

import app.qomo.apiusers.application.port.out.PasswordHasherPort;
import app.qomo.apiusers.domain.model.PasswordHash;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Outbound adapter for {@link PasswordHasherPort} implemented with Spring Security's {@link
 * BCryptPasswordEncoder}.
 *
 * <p>The adapter processes raw user passwords and returns only the BCrypt hash wrapped by the
 * domain model. Raw passwords and generated hashes are sensitive values and must not be logged in
 * clear text by callers or infrastructure diagnostics.
 */
public final class BcryptPasswordHasherAdapter implements PasswordHasherPort {

  private final BCryptPasswordEncoder encoder;

  /**
   * Creates the adapter with the configured BCrypt encoder.
   *
   * @param encoder encoder carrying the application's BCrypt strength and random salt generation
   *     strategy
   * @throws NullPointerException if {@code encoder} is {@code null}
   */
  public BcryptPasswordHasherAdapter(BCryptPasswordEncoder encoder) {
    this.encoder = Objects.requireNonNull(encoder);
  }

  /**
   * Hashes a non-blank raw password with BCrypt.
   *
   * @param rawPassword clear-text password supplied by the application layer; this value is
   *     sensitive and must not be logged
   * @return domain password hash containing the encoded BCrypt value
   * @throws IllegalArgumentException if {@code rawPassword} is {@code null} or blank
   */
  @Override
  public PasswordHash hash(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new IllegalArgumentException("rawPassword cannot be blank");
    }
    return new PasswordHash(encoder.encode(rawPassword));
  }
}
