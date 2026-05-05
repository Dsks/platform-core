package app.qomo.apiusers.infrastructure.adapter.out.crypto;

import app.qomo.apiusers.application.port.out.PasswordVerifierPort;
import app.qomo.apiusers.domain.model.PasswordHash;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Outbound adapter for {@link PasswordVerifierPort} implemented with Spring Security's {@link
 * BCryptPasswordEncoder}.
 *
 * <p>It encapsulates BCrypt verification for the application layer. Both the raw password and the
 * stored hash are authentication secrets and must not be written to logs or diagnostic messages in
 * clear text.
 */
public class BCryptPasswordVerifierAdapter implements PasswordVerifierPort {

  private final BCryptPasswordEncoder encoder;

  /**
   * Creates the verifier with the configured BCrypt encoder.
   *
   * @param encoder encoder used to compare clear-text candidates against stored BCrypt hashes
   * @throws NullPointerException if {@code encoder} is {@code null}
   */
  public BCryptPasswordVerifierAdapter(BCryptPasswordEncoder encoder) {
    this.encoder = Objects.requireNonNull(encoder, "encoder cannot be null");
  }

  /**
   * Compares a clear-text password candidate with a stored domain hash.
   *
   * @param rawPassword clear-text candidate supplied during authentication; sensitive and not safe
   *     to log
   * @param passwordHash stored BCrypt hash from the user aggregate
   * @return {@code true} when the encoder accepts the candidate for the stored hash
   */
  @Override
  public boolean matches(String rawPassword, PasswordHash passwordHash) {
    return encoder.matches(rawPassword, passwordHash.value());
  }
}
