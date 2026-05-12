package app.platformcore.apiusers.domain.model;

import java.util.Objects;

/**
 * Value object for a hashed password.
 *
 * <p>The domain only accepts pre-hashed values and never exposes them through {@code toString()}.
 */
public record PasswordHash(String value) {

  /** Creates a non-blank password hash value. */
  public PasswordHash {
    Objects.requireNonNull(value);
    if (value.isBlank()) {
      throw new IllegalArgumentException("Password hash cannot be blank");
    }
  }

  @Override
  public String toString() {
    return "[PROTECTED]";
  }
}
